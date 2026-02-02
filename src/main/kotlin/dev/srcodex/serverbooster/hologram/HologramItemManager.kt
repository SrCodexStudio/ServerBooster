package dev.srcodex.serverbooster.hologram

import dev.srcodex.serverbooster.ServerBoosterPlugin
import dev.srcodex.serverbooster.util.MinecraftVersion
import dev.srcodex.serverbooster.util.SchedulerUtil
import kotlinx.coroutines.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.ItemMergeEvent
import org.bukkit.event.entity.ItemSpawnEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryPickupItemEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.text.NumberFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Advanced Item Stacking System with unlimited stack support.
 *
 * Uses PersistentDataContainer to store real amounts (up to Long.MAX_VALUE)
 * while keeping ItemStack.amount at 1 for safe Minecraft serialization.
 *
 * Anti-dupe protections:
 * - UUID-based item tracking during operations
 * - Atomic merge operations with validation
 * - PDC integrity checks before any modification
 */
class HologramItemManager(private val plugin: ServerBoosterPlugin) : Listener {

    private val config get() = plugin.configManager.hologramConfig

    // PDC Keys for storing real amount
    private val amountKey = NamespacedKey(plugin, "stacked_amount")
    private val checksumKey = NamespacedKey(plugin, "stack_checksum")

    // Track items currently being processed (anti-dupe)
    private val processingItems = ConcurrentHashMap.newKeySet<UUID>()

    // Track player-dropped items for glow
    private val playerDroppedItems = ConcurrentHashMap.newKeySet<UUID>()

    // Translation cache for item names
    private val translationCache = ConcurrentHashMap<Material, Component>()

    // Coroutine scope for async operations
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Number formatter for large amounts
    private val numberFormat = NumberFormat.getNumberInstance(Locale.US)

    // Throttling for merge operations - prevent excessive coroutine spawning
    private val pendingMergeItems = ConcurrentHashMap.newKeySet<UUID>()
    private val maxPendingMerges = 50  // Limit concurrent merge operations

    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
        plugin.logger.info("Hologram Item Manager initialized with unlimited stacking support")
    }

    fun cleanup() {
        HandlerList.unregisterAll(this)
        scope.cancel()
        processingItems.clear()
        playerDroppedItems.clear()
        pendingMergeItems.clear()
        translationCache.clear()
        recentlyProcessedChunks.clear()
    }

    // ══════════════════════════════════════════════════════════════════════
    // PDC AMOUNT MANAGEMENT - Core of unlimited stacking
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Gets the real stacked amount from PDC, or ItemStack amount if not set.
     * Thread-safe and validates data integrity.
     */
    private fun getRealAmount(item: Item): Long {
        val pdc = item.persistentDataContainer

        // Check if we have a stored amount
        if (pdc.has(amountKey, PersistentDataType.LONG)) {
            val storedAmount = pdc.get(amountKey, PersistentDataType.LONG) ?: return item.itemStack.amount.toLong()

            // Validate checksum to detect corruption/tampering
            val expectedChecksum = calculateChecksum(item.itemStack.type, storedAmount)
            val storedChecksum = pdc.get(checksumKey, PersistentDataType.LONG) ?: 0L

            if (storedChecksum == expectedChecksum) {
                return storedAmount
            } else {
                // Checksum mismatch - possible tampering, reset to safe value
                plugin.logger.warning("Stack checksum mismatch for ${item.itemStack.type} - resetting to 1")
                setRealAmount(item, 1L)
                return 1L
            }
        }

        return item.itemStack.amount.toLong()
    }

    /**
     * Sets the real stacked amount in PDC with integrity checksum.
     * ItemStack.amount is kept at 1 for safe serialization.
     */
    private fun setRealAmount(item: Item, amount: Long) {
        if (amount <= 0) {
            item.remove()
            return
        }

        val pdc = item.persistentDataContainer
        val material = item.itemStack.type

        // Store real amount and checksum
        pdc.set(amountKey, PersistentDataType.LONG, amount)
        pdc.set(checksumKey, PersistentDataType.LONG, calculateChecksum(material, amount))

        // Keep ItemStack amount at 1 for safe serialization
        val stack = item.itemStack.clone()
        stack.amount = 1
        item.setItemStack(stack)
    }

    /**
     * Calculates a checksum to verify data integrity.
     * Prevents item duplication through PDC manipulation.
     */
    private fun calculateChecksum(material: Material, amount: Long): Long {
        // Simple but effective checksum using material hash and amount
        val materialHash = material.name.hashCode().toLong()
        return (materialHash xor amount) * 31 + (amount shr 16)
    }

    /**
     * Checks if an item has PDC stacking data
     */
    private fun hasStackData(item: Item): Boolean {
        return item.persistentDataContainer.has(amountKey, PersistentDataType.LONG)
    }

    // ══════════════════════════════════════════════════════════════════════
    // EVENT HANDLERS
    // ══════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onItemSpawn(event: ItemSpawnEvent) {
        val item = event.entity
        if (!isInConfiguredWorld(item)) return
        if (isBlacklisted(item.itemStack.type)) return

        // Initialize PDC if item has more than 1
        if (item.itemStack.amount > 1 && !hasStackData(item)) {
            setRealAmount(item, item.itemStack.amount.toLong())
        }

        if (config.mergeOnlyPlayerDrops && !playerDroppedItems.contains(item.uniqueId)) return

        // Apply glow if configured
        if (!config.hologram.glow.onlyPlayerDroppedItems) {
            applyGlow(item)
        }

        // Try to merge with nearby items (async search, sync merge)
        // THROTTLED: Only launch if under the pending merge limit
        if (config.forceMerge.enabled && pendingMergeItems.size < maxPendingMerges) {
            if (pendingMergeItems.add(item.uniqueId)) {
                scope.launch {
                    try {
                        delay(50) // Small delay to let other items spawn
                        val nearbyItem = findNearbyMergeableItemAsync(item)
                        if (nearbyItem != null) {
                            SchedulerUtil.runTask {
                                if (!item.isDead && !nearbyItem.isDead) {
                                    tryMergeItems(item, nearbyItem)
                                }
                            }
                        }
                    } finally {
                        pendingMergeItems.remove(item.uniqueId)
                    }
                }
            }
        }

        // Update hologram
        updateHologramDisplay(item)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerDrop(event: PlayerDropItemEvent) {
        val item = event.itemDrop
        if (!isInConfiguredWorld(item)) return
        if (isBlacklisted(item.itemStack.type)) return

        playerDroppedItems.add(item.uniqueId)

        // Initialize PDC stacking
        if (item.itemStack.amount > 1 && !hasStackData(item)) {
            setRealAmount(item, item.itemStack.amount.toLong())
        }

        // Apply glow
        if (config.hologram.glow.onlyPlayerDroppedItems || config.hologram.glow.enabled) {
            applyGlow(item)
        }

        updateHologramDisplay(item)

        // Try merge async - THROTTLED
        if (config.forceMerge.enabled && pendingMergeItems.size < maxPendingMerges) {
            if (pendingMergeItems.add(item.uniqueId)) {
                scope.launch {
                    try {
                        delay(50)
                        val nearbyItem = findNearbyMergeableItemAsync(item)
                        if (nearbyItem != null) {
                            SchedulerUtil.runTask {
                                if (!item.isDead && !nearbyItem.isDead) {
                                    tryMergeItems(item, nearbyItem)
                                }
                            }
                        }
                    } finally {
                        pendingMergeItems.remove(item.uniqueId)
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onItemMerge(event: ItemMergeEvent) {
        // Cancel vanilla merge - we handle it ourselves with PDC
        if (isInConfiguredWorld(event.entity)) {
            event.isCancelled = true

            // Merge using our system
            val source = event.entity
            val target = event.target

            if (!processingItems.contains(source.uniqueId) && !processingItems.contains(target.uniqueId)) {
                tryMergeItems(source, target)
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPlayerPickup(event: EntityPickupItemEvent) {
        if (event.entity !is Player) return
        val player = event.entity as Player
        val item = event.item

        if (!isInConfiguredWorld(item)) return

        val realAmount = getRealAmount(item)

        // If it's a normal stack (<=64), let vanilla handle it
        if (realAmount <= item.itemStack.type.maxStackSize && !hasStackData(item)) {
            playerDroppedItems.remove(item.uniqueId)
            return
        }

        // Cancel and handle manually for large stacks
        event.isCancelled = true

        // Prevent concurrent pickup
        if (!processingItems.add(item.uniqueId)) return

        try {
            val maxStack = item.itemStack.type.maxStackSize
            val pickupAmount = minOf(realAmount, maxStack.toLong())

            // Check inventory space
            if (player.inventory.firstEmpty() == -1) {
                // Try to add to existing stacks
                var added = 0L
                for (slot in player.inventory.contents.indices) {
                    val slotItem = player.inventory.getItem(slot) ?: continue
                    if (slotItem.isSimilar(item.itemStack)) {
                        val canAdd = maxStack - slotItem.amount
                        if (canAdd > 0) {
                            val toAdd = minOf(canAdd.toLong(), pickupAmount - added)
                            slotItem.amount += toAdd.toInt()
                            added += toAdd
                            if (added >= pickupAmount) break
                        }
                    }
                }

                if (added == 0L) {
                    processingItems.remove(item.uniqueId)
                    return
                }

                val newAmount = realAmount - added
                if (newAmount <= 0) {
                    item.remove()
                } else {
                    setRealAmount(item, newAmount)
                    updateHologramDisplay(item)
                }

                playPickupSound(player)
            } else {
                // Add to inventory
                val pickup = item.itemStack.clone()
                pickup.amount = pickupAmount.toInt()

                val remaining = player.inventory.addItem(pickup)
                val actualPickup = pickupAmount - (remaining.values.sumOf { it.amount })

                val newAmount = realAmount - actualPickup
                if (newAmount <= 0) {
                    item.remove()
                } else {
                    setRealAmount(item, newAmount)
                    item.pickupDelay = config.pickupDelay.legitStacks
                    updateHologramDisplay(item)
                }

                playPickupSound(player)
            }
        } finally {
            processingItems.remove(item.uniqueId)
            playerDroppedItems.remove(item.uniqueId)
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onHopperPickup(event: InventoryPickupItemEvent) {
        val item = event.item
        if (!isInConfiguredWorld(item)) return

        val realAmount = getRealAmount(item)

        // Normal stack, let vanilla handle
        if (realAmount <= item.itemStack.type.maxStackSize && !hasStackData(item)) return

        event.isCancelled = true

        if (!processingItems.add(item.uniqueId)) return

        try {
            val maxStack = item.itemStack.type.maxStackSize
            val pickupAmount = minOf(realAmount, maxStack.toLong())

            val pickup = item.itemStack.clone()
            pickup.amount = pickupAmount.toInt()

            val remaining = event.inventory.addItem(pickup)
            val actualPickup = pickupAmount - (remaining.values.sumOf { it.amount })

            if (actualPickup > 0) {
                val newAmount = realAmount - actualPickup
                if (newAmount <= 0) {
                    item.remove()
                } else {
                    setRealAmount(item, newAmount)
                    updateHologramDisplay(item)
                }
            }
        } finally {
            processingItems.remove(item.uniqueId)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        if (!isInConfiguredWorld(event.entity)) return

        // Apply glow to death drops
        if (config.hologram.glow.enabled) {
            val player = event.entity
            SchedulerUtil.runTaskLater(10L) {
                for (entity in player.location.world?.getNearbyEntities(player.location, 3.0, 3.0, 3.0) ?: emptyList()) {
                    if (entity is Item && !isBlacklisted(entity.itemStack.type)) {
                        applyGlow(entity)
                        updateHologramDisplay(entity)
                    }
                }
            }
        }
    }

    // Throttle chunk load processing
    private val recentlyProcessedChunks = ConcurrentHashMap<Long, Long>()
    private val chunkProcessCooldownMs = 5000L  // 5 second cooldown per chunk

    @EventHandler(priority = EventPriority.MONITOR)
    fun onChunkLoad(event: ChunkLoadEvent) {
        if (!config.worlds.contains(event.world.name)) return

        // Throttle: Skip if this chunk was recently processed
        val chunkKey = (event.chunk.x.toLong() shl 32) or (event.chunk.z.toLong() and 0xFFFFFFFFL)
        val now = System.currentTimeMillis()
        val lastProcessed = recentlyProcessedChunks[chunkKey] ?: 0L
        if (now - lastProcessed < chunkProcessCooldownMs) return
        recentlyProcessedChunks[chunkKey] = now

        // Cleanup old entries periodically (every 100 chunks)
        if (recentlyProcessedChunks.size > 100) {
            val cutoff = now - chunkProcessCooldownMs
            recentlyProcessedChunks.entries.removeIf { it.value < cutoff }
        }

        // Update holograms for loaded items with PDC data - direct task, no coroutine needed
        SchedulerUtil.runTaskLater(5L) {  // 5 tick delay instead of 100ms coroutine
            if (!event.chunk.isLoaded) return@runTaskLater
            for (entity in event.chunk.entities) {
                if (entity is Item && hasStackData(entity)) {
                    updateHologramDisplay(entity)
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // MERGE SYSTEM - Anti-dupe protected
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Attempts to merge two items with anti-dupe protection.
     * Uses atomic operations and validates data before merge.
     */
    private fun tryMergeItems(source: Item, target: Item): Boolean {
        // Prevent concurrent operations on same items
        if (!processingItems.add(source.uniqueId)) return false
        if (!processingItems.add(target.uniqueId)) {
            processingItems.remove(source.uniqueId)
            return false
        }

        try {
            // Validate items are still valid
            if (source.isDead || target.isDead) return false
            if (!source.itemStack.isSimilar(target.itemStack)) return false

            val sourceAmount = getRealAmount(source)
            val targetAmount = getRealAmount(target)
            val combined = sourceAmount + targetAmount

            // Check max stack limit from config
            val maxStack = config.forceMerge.maxStack.toLong()
            if (maxStack > 0 && combined > maxStack) return false

            // Perform merge - target gets all items
            setRealAmount(target, combined)
            target.ticksLived = 1
            target.pickupDelay = 15

            // Remove source
            source.remove()

            // Cleanup tracking
            playerDroppedItems.remove(source.uniqueId)

            updateHologramDisplay(target)

            return true
        } finally {
            processingItems.remove(source.uniqueId)
            processingItems.remove(target.uniqueId)
        }
    }

    /**
     * Async search for nearby mergeable items using coroutines.
     */
    private suspend fun findNearbyMergeableItemAsync(item: Item): Item? = withContext(Dispatchers.Default) {
        if (item.isDead) return@withContext null
        if (!config.forceMerge.enabled) return@withContext null

        val radius = config.forceMerge.radius.toDouble()
        val itemStack = item.itemStack
        val itemUuid = item.uniqueId

        // Get nearby entities on main thread
        val nearbyEntities = suspendCancellableCoroutine<List<Entity>> { cont ->
            SchedulerUtil.runTask {
                if (item.isDead) {
                    cont.resume(emptyList()) {}
                } else {
                    cont.resume(item.getNearbyEntities(radius, radius, radius)) {}
                }
            }
        }

        // Find best merge candidate
        for (entity in nearbyEntities) {
            if (entity.type != EntityType.DROPPED_ITEM) continue
            val nearby = entity as Item
            if (nearby.uniqueId == itemUuid) continue
            if (nearby.isDead) continue
            if (nearby.pickupDelay > 1000) continue
            if (processingItems.contains(nearby.uniqueId)) continue

            val similar = if (config.forceMerge.preciseSimilarityCheck) {
                arePreciselySimilar(itemStack, nearby.itemStack)
            } else {
                nearby.itemStack.isSimilar(itemStack)
            }

            if (similar) return@withContext nearby
        }

        return@withContext null
    }

    private fun arePreciselySimilar(a: ItemStack, b: ItemStack): Boolean {
        if (a.type != b.type) return false
        if (a.hasItemMeta() != b.hasItemMeta()) return false

        if (a.hasItemMeta() && b.hasItemMeta()) {
            val metaA = a.itemMeta!!
            val metaB = b.itemMeta!!

            if (metaA.hasDisplayName() != metaB.hasDisplayName()) return false
            if (metaA.hasLore() != metaB.hasLore()) return false
            if (metaA.hasEnchants() != metaB.hasEnchants()) return false

            // Deep comparison
            if (metaA.hasDisplayName() && metaA.displayName() != metaB.displayName()) return false
            if (metaA.hasLore() && metaA.lore() != metaB.lore()) return false
            if (metaA.hasEnchants() && metaA.enchants != metaB.enchants) return false
        }

        return true
    }

    // ══════════════════════════════════════════════════════════════════════
    // DISPLAY & UTILITY
    // ══════════════════════════════════════════════════════════════════════

    private fun updateHologramDisplay(item: Item) {
        if (!config.hologram.enabled) {
            item.isCustomNameVisible = false
            return
        }

        val realAmount = getRealAmount(item)
        item.isCustomNameVisible = true

        val component = buildHologramComponent(item.itemStack, realAmount)
        plugin.nmsManager.setCustomName(item, component)
    }

    private fun buildHologramComponent(stack: ItemStack, amount: Long): Component {
        val format = config.hologram.format
        val meta = stack.itemMeta

        val itemName: Component = if (meta != null && meta.hasDisplayName()) {
            meta.displayName() ?: Component.text(stack.type.name)
        } else {
            translationCache.getOrPut(stack.type) {
                val key = plugin.nmsManager.getTranslationKey(stack.type)
                Component.translatable(key)
            }
        }

        // Format large numbers nicely
        val amountStr = formatAmount(amount)

        val formatted = ChatColor.translateAlternateColorCodes('&', format)
            .replace("{name}", "%name%")
            .replace("{amount}", amountStr)

        val parts = formatted.split("%name%")
        return if (parts.size == 2) {
            LegacyComponentSerializer.legacySection().deserialize(parts[0])
                .append(itemName)
                .append(LegacyComponentSerializer.legacySection().deserialize(parts[1]))
        } else {
            LegacyComponentSerializer.legacySection().deserialize(formatted)
        }
    }

    /**
     * Formats large numbers for display (e.g., 1,234,567 or 1.2M)
     */
    private fun formatAmount(amount: Long): String {
        return when {
            amount >= 1_000_000_000 -> String.format("%.1fB", amount / 1_000_000_000.0)
            amount >= 1_000_000 -> String.format("%.1fM", amount / 1_000_000.0)
            amount >= 10_000 -> String.format("%.1fK", amount / 1_000.0)
            else -> numberFormat.format(amount)
        }
    }

    private fun applyGlow(item: Item) {
        if (!config.hologram.glow.enabled) return
        if (!MinecraftVersion.isAtLeast(1, 9)) return
        item.isGlowing = true
    }

    private fun playPickupSound(player: Player) {
        try {
            player.playSound(
                player.location,
                org.bukkit.Sound.ENTITY_ITEM_PICKUP,
                0.2f,
                0.7f + (Math.random().toFloat() * 0.6f)
            )
        } catch (_: Exception) {}
    }

    private fun isInConfiguredWorld(entity: Entity): Boolean {
        return config.worlds.contains(entity.world.name)
    }

    private fun isBlacklisted(material: Material): Boolean {
        return config.blacklist.contains(material.name)
    }

    fun clearHolograms() {
        for (worldName in config.worlds) {
            val world = Bukkit.getWorld(worldName) ?: continue
            for (entity in world.entities) {
                if (entity is Item) {
                    entity.isCustomNameVisible = false
                }
            }
        }
    }

    /**
     * Debug command - get real amount of item player is looking at
     */
    fun getItemInfo(item: Item): String {
        val realAmount = getRealAmount(item)
        val hasData = hasStackData(item)
        return "Amount: ${formatAmount(realAmount)} (PDC: $hasData)"
    }
}
