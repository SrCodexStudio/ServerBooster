package dev.srcodex.serverbooster.hologram

import dev.srcodex.serverbooster.ServerBoosterPlugin
import dev.srcodex.serverbooster.util.MinecraftVersion
import dev.srcodex.serverbooster.util.SchedulerUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.ItemDespawnEvent
import org.bukkit.event.entity.ItemMergeEvent
import org.bukkit.event.entity.ItemSpawnEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryPickupItemEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerPickupItemEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.inventory.ItemStack
import java.util.concurrent.ConcurrentHashMap

class HologramItemManager(private val plugin: ServerBoosterPlugin) : Listener {

    private val config get() = plugin.configManager.hologramConfig

    // Track player-dropped items
    private val playerDroppedItems = ConcurrentHashMap.newKeySet<Int>()

    // Track items being picked up
    private val pickingUpItems = ConcurrentHashMap.newKeySet<Int>()

    // Translation cache
    private val translationCache = ConcurrentHashMap<Material, Component>()

    // Maximum stack size Minecraft can serialize (vanilla limit)
    companion object {
        const val MAX_SERIALIZABLE_STACK = 99
    }

    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

    fun cleanup() {
        HandlerList.unregisterAll(this)
        playerDroppedItems.clear()
        pickingUpItems.clear()
        translationCache.clear()
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onItemSpawn(event: ItemSpawnEvent) {
        if (event.isCancelled) return

        val item = event.entity
        if (!isInConfiguredWorld(item)) return
        if (isBlacklisted(item.itemStack.type)) return

        if (config.mergeOnlyPlayerDrops) return

        // Apply glow if not player-only
        if (!config.hologram.glow.onlyPlayerDroppedItems) {
            applyGlow(item, null)
        }

        // Skip if already processed
        if (playerDroppedItems.contains(item.entityId)) return

        // Try to merge with nearby items
        forceMergeItem(item)

        // Update hologram display
        updateHologramDisplay(item)
    }

    @EventHandler
    fun onPlayerDrop(event: PlayerDropItemEvent) {
        if (event.isCancelled) return

        val item = event.itemDrop
        if (!isInConfiguredWorld(item)) return
        if (isBlacklisted(item.itemStack.type)) return

        playerDroppedItems.add(item.entityId)

        // Apply glow for player-dropped items
        if (config.hologram.glow.onlyPlayerDroppedItems) {
            applyGlow(item, event.player)
        }

        // Update hologram and try merge
        updateHologramDisplay(item)
        forceMergeItem(item)
    }

    @EventHandler
    fun onItemMerge(event: ItemMergeEvent) {
        if (event.isCancelled) return
        if (!isInConfiguredWorld(event.entity)) return

        val source = event.entity
        val target = event.target

        // Keep the older item (more ticks lived)
        if (source.ticksLived > target.ticksLived) {
            event.isCancelled = true
            mergeItems(target, source)
        } else {
            updateHologramDisplay(target)
        }
    }

    /**
     * Handle player death - split oversized stacks to prevent serialization errors
     */
    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        if (!isInConfiguredWorld(event.entity)) return

        val player = event.entity

        // Split oversized stacks in death drops to prevent serialization errors
        val newDrops = mutableListOf<ItemStack>()
        val iterator = event.drops.iterator()

        while (iterator.hasNext()) {
            val stack = iterator.next()
            if (stack.amount > MAX_SERIALIZABLE_STACK) {
                iterator.remove()
                newDrops.addAll(splitStack(stack))
            }
        }

        event.drops.addAll(newDrops)

        // Apply glow to dropped items from death (delayed)
        if (config.hologram.glow.onlyPlayerDroppedItems) {
            SchedulerUtil.runTaskLater(20L) {
                for (entity in player.getNearbyEntities(5.0, 5.0, 5.0)) {
                    if (entity.type != EntityType.DROPPED_ITEM) continue
                    val item = entity as Item
                    if (item.pickupDelay > 1000) continue
                    if (isBlacklisted(item.itemStack.type)) continue

                    applyGlow(item, player)
                }
            }
        }
    }

    /**
     * Handle chunk unload - split oversized item stacks to prevent serialization errors
     */
    @EventHandler(priority = EventPriority.LOWEST)
    fun onChunkUnload(event: ChunkUnloadEvent) {
        if (!config.worlds.contains(event.world.name)) return

        for (entity in event.chunk.entities) {
            if (entity.type != EntityType.DROPPED_ITEM) continue

            val item = entity as Item
            val stack = item.itemStack

            if (stack.amount > MAX_SERIALIZABLE_STACK) {
                // Split the oversized stack
                val location = item.location
                val world = item.world
                val velocity = item.velocity

                // Set the original item to max allowed
                val newStack = stack.clone()
                newStack.amount = MAX_SERIALIZABLE_STACK
                item.setItemStack(newStack)

                // Drop the excess as new items
                var remaining = stack.amount - MAX_SERIALIZABLE_STACK
                while (remaining > 0) {
                    val dropAmount = minOf(remaining, MAX_SERIALIZABLE_STACK)
                    val dropStack = stack.clone()
                    dropStack.amount = dropAmount

                    val droppedItem = world.dropItem(location, dropStack)
                    droppedItem.velocity = velocity
                    droppedItem.pickupDelay = item.pickupDelay

                    remaining -= dropAmount
                }

                updateHologramDisplay(item)
            }
        }
    }

    /**
     * Splits an oversized stack into multiple stacks of MAX_SERIALIZABLE_STACK or less
     */
    private fun splitStack(stack: ItemStack): List<ItemStack> {
        val result = mutableListOf<ItemStack>()
        var remaining = stack.amount

        while (remaining > 0) {
            val amount = minOf(remaining, MAX_SERIALIZABLE_STACK)
            val newStack = stack.clone()
            newStack.amount = amount
            result.add(newStack)
            remaining -= amount
        }

        return result
    }

    @EventHandler
    fun onPlayerPickup(event: PlayerPickupItemEvent) {
        if (event.isCancelled) return
        if (!isInConfiguredWorld(event.item)) return

        val item = event.item
        val stack = item.itemStack

        playerDroppedItems.remove(item.entityId)
        pickingUpItems.add(item.entityId)

        // Handle oversized stacks
        if (stack.amount > stack.type.maxStackSize) {
            event.isCancelled = true

            if (event.player.inventory.firstEmpty() == -1) return

            if (stack.maxStackSize == 1) {
                // Unstackable items - pick up one at a time
                stack.amount = stack.amount - 1
                item.setItemStack(stack)
                item.pickupDelay = item.pickupDelay + config.pickupDelay.unstackableItemsStacks

                val pickup = stack.clone()
                pickup.amount = 1
                event.player.inventory.addItem(pickup)

                playPickupSound(event.player)
                updateHologramDisplay(item)
            } else {
                // Stackable items - pick up half at a time
                item.pickupDelay = item.pickupDelay + config.pickupDelay.legitStacks
                item.ticksLived = 1

                val pickupAmount = stack.maxStackSize / 2
                stack.amount = stack.amount - pickupAmount
                item.setItemStack(stack)

                val pickup = stack.clone()
                pickup.amount = pickupAmount
                event.player.inventory.addItem(pickup)

                playPickupSound(event.player)
                updateHologramDisplay(item)
            }
        }
    }

    @EventHandler
    fun onHopperPickup(event: InventoryPickupItemEvent) {
        if (event.isCancelled) return
        if (!isInConfiguredWorld(event.item)) return

        val item = event.item
        val stack = item.itemStack

        // Handle oversized unstackable items
        if (stack.maxStackSize == 1 && stack.amount > 1) {
            event.isCancelled = true

            val pickup = stack.clone()
            pickup.amount = 1
            stack.amount = stack.amount - 1
            updateHologramDisplay(item)

            event.inventory.addItem(pickup).forEach { (_, remaining) ->
                item.world.dropItem(item.location, remaining)
            }
            return
        }

        // Handle oversized stackable items
        if (stack.amount > stack.maxStackSize) {
            event.isCancelled = true

            val pickupAmount = stack.maxStackSize / 2
            val pickup = stack.clone()
            pickup.amount = pickupAmount
            stack.amount = stack.amount - pickupAmount
            updateHologramDisplay(item)

            event.inventory.addItem(pickup).forEach { (_, remaining) ->
                item.world.dropItem(item.location, remaining)
            }
        }
    }

    private fun isInConfiguredWorld(entity: Entity): Boolean {
        return config.worlds.contains(entity.world.name)
    }

    private fun isBlacklisted(material: Material): Boolean {
        return config.blacklist.contains(material.name)
    }

    private fun forceMergeItem(item: Item) {
        if (!config.forceMerge.enabled) return

        val nearby = findNearbyMergeableItem(item) ?: return

        val combined = item.itemStack.amount + nearby.itemStack.amount

        // Check max stack limit
        if (config.forceMerge.maxStack > 0 && combined > config.forceMerge.maxStack) return

        // Skip items being picked up
        if (pickingUpItems.contains(nearby.entityId)) {
            pickingUpItems.remove(nearby.entityId)
            return
        }

        // Merge into nearby item
        val newStack = nearby.itemStack.clone()
        newStack.amount = combined
        nearby.setItemStack(newStack)
        nearby.ticksLived = 1
        nearby.pickupDelay = 15

        updateHologramDisplay(nearby)
        item.remove()
    }

    private fun mergeItems(source: Item, target: Item) {
        val combined = source.itemStack.amount + target.itemStack.amount

        // Check max stack limit
        if (config.forceMerge.maxStack > 0 && combined > config.forceMerge.maxStack) return

        val newStack = target.itemStack.clone()
        newStack.amount = combined
        target.setItemStack(newStack)
        target.ticksLived = 1
        target.pickupDelay = 15

        updateHologramDisplay(target)
        source.remove()
    }

    private fun findNearbyMergeableItem(item: Item): Item? {
        val radius = config.forceMerge.radius.toDouble()

        for (entity in item.getNearbyEntities(radius, radius, radius)) {
            if (entity.type != EntityType.DROPPED_ITEM) continue
            val nearby = entity as Item
            if (nearby.uniqueId == item.uniqueId) continue
            if (nearby.pickupDelay > 1000) continue

            val similar = if (config.forceMerge.preciseSimilarityCheck) {
                arePreciselySimilar(item.itemStack, nearby.itemStack)
            } else {
                nearby.itemStack.isSimilar(item.itemStack)
            }

            if (similar) return nearby
        }

        return null
    }

    private fun arePreciselySimilar(a: ItemStack, b: ItemStack): Boolean {
        if (a.type != b.type) return false
        if (a.hasItemMeta() != b.hasItemMeta()) return false

        if (a.hasItemMeta() && b.hasItemMeta()) {
            val metaA = a.itemMeta!!
            val metaB = b.itemMeta!!

            if (metaA.hasDisplayName() != metaB.hasDisplayName()) return false
            if (metaA.hasDisplayName() && metaA.displayName != metaB.displayName) return false

            if (metaA.hasLore() != metaB.hasLore()) return false
            if (metaA.hasLore() && metaA.lore != metaB.lore) return false

            if (metaA.hasEnchants() != metaB.hasEnchants()) return false
            if (metaA.hasEnchants() && metaA.enchants != metaB.enchants) return false
        }

        return true
    }

    private fun updateHologramDisplay(item: Item) {
        if (!config.hologram.enabled) {
            item.isCustomNameVisible = false
            return
        }

        item.isCustomNameVisible = true

        val component = buildHologramComponent(item.itemStack, item.itemStack.amount)
        plugin.nmsManager.setCustomName(item, component)
    }

    private fun buildHologramComponent(stack: ItemStack, amount: Int): Component {
        val format = config.hologram.format
        val meta = stack.itemMeta

        val itemName: Component = if (meta != null && meta.hasDisplayName()) {
            // Use display name
            LegacyComponentSerializer.legacySection().deserialize(meta.displayName!!)
        } else {
            // Use translation key
            translationCache.getOrPut(stack.type) {
                val key = plugin.nmsManager.getTranslationKey(stack.type)
                Component.translatable(key)
            }
        }

        // Parse format string
        val formatted = ChatColor.translateAlternateColorCodes('&', format)
            .replace("{name}", "%name%")
            .replace("{amount}", amount.toString())

        // Build component
        val parts = formatted.split("%name%")
        return if (parts.size == 2) {
            LegacyComponentSerializer.legacySection().deserialize(parts[0])
                .append(itemName)
                .append(LegacyComponentSerializer.legacySection().deserialize(parts[1]))
        } else {
            LegacyComponentSerializer.legacySection().deserialize(formatted)
        }
    }

    private fun applyGlow(item: Item, player: Player?) {
        if (!config.hologram.glow.enabled) return
        if (!MinecraftVersion.isAtLeast(1, 9)) return

        // Simple glow using Bukkit API
        item.isGlowing = true
    }

    private fun playPickupSound(player: Player) {
        try {
            player.playSound(
                player.location,
                org.bukkit.Sound.ENTITY_ITEM_PICKUP,
                0.2f,
                (0.7f + Math.random().toFloat() * 0.6f)
            )
        } catch (e: Exception) {
            // Sound may not exist in all versions
        }
    }

    fun clearHolograms() {
        for (worldName in config.worlds) {
            val world = Bukkit.getWorld(worldName) ?: continue
            for (entity in world.entities) {
                if (entity.type == EntityType.DROPPED_ITEM) {
                    (entity as Item).isCustomNameVisible = false
                }
            }
        }
    }
}
