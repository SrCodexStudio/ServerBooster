package dev.srcodex.serverbooster.chunk

import dev.srcodex.serverbooster.ServerBoosterPlugin
import dev.srcodex.serverbooster.config.ChunkBlockLimitsConfig
import dev.srcodex.serverbooster.util.SchedulerUtil
import kotlinx.coroutines.*
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.EntityType
import org.bukkit.entity.ItemFrame
import org.bukkit.entity.Painting
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.hanging.HangingPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * Limits the number of specific block types within a radius
 * Uses the same radius-based detection as EntityLimiter
 */
class ChunkBlockLimiter(private val plugin: ServerBoosterPlugin) : Listener {

    private val config: ChunkBlockLimitsConfig
        get() = plugin.configManager.chunkBlockLimitsConfig

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Cache for radius block counts (cleared periodically)
    private val radiusCountCache = ConcurrentHashMap<String, MutableMap<String, Int>>()
    private var cacheCleanupTask: Any? = null

    // Block category mappings
    private val blockCategories = mapOf(
        // Storage
        "chests" to setOf(Material.CHEST),
        "trapped-chests" to setOf(Material.TRAPPED_CHEST),
        "barrels" to setOf(Material.BARREL),
        "ender-chests" to setOf(Material.ENDER_CHEST),

        // Shulker boxes (all colors)
        "shulker-boxes" to Material.entries.filter { it.name.contains("SHULKER_BOX") }.toSet(),

        // Redstone
        "hoppers" to setOf(Material.HOPPER),
        "pistons" to setOf(Material.PISTON, Material.STICKY_PISTON),
        "observers" to setOf(Material.OBSERVER),
        "dispensers" to setOf(Material.DISPENSER),
        "droppers" to setOf(Material.DROPPER),
        "redstone-blocks" to setOf(Material.REDSTONE_BLOCK),
        "comparators" to setOf(Material.COMPARATOR),
        "repeaters" to setOf(Material.REPEATER),
        "daylight-detectors" to setOf(Material.DAYLIGHT_DETECTOR),
        "target-blocks" to setOf(Material.TARGET),

        // Workstations
        "crafting-tables" to setOf(Material.CRAFTING_TABLE),
        "furnaces" to setOf(Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER),
        "anvils" to setOf(Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL),
        "enchanting-tables" to setOf(Material.ENCHANTING_TABLE),
        "brewing-stands" to setOf(Material.BREWING_STAND),
        "grindstones" to setOf(Material.GRINDSTONE),
        "cartography-tables" to setOf(Material.CARTOGRAPHY_TABLE),
        "fletching-tables" to setOf(Material.FLETCHING_TABLE),
        "smithing-tables" to setOf(Material.SMITHING_TABLE),
        "looms" to setOf(Material.LOOM),
        "stonecutters" to setOf(Material.STONECUTTER),
        "lecterns" to setOf(Material.LECTERN),

        // Lighting
        "beacons" to setOf(Material.BEACON),
        "conduits" to setOf(Material.CONDUIT),
        "end-rods" to setOf(Material.END_ROD),

        // Beds (all colors)
        "beds" to Material.entries.filter { it.name.endsWith("_BED") }.toSet(),

        // Respawn anchors
        "respawn-anchors" to setOf(Material.RESPAWN_ANCHOR),

        // Signs (all types)
        "signs" to Material.entries.filter {
            it.name.contains("SIGN") && !it.name.contains("HANGING")
        }.toSet(),

        // Hanging signs
        "hanging-signs" to Material.entries.filter {
            it.name.contains("HANGING") && it.name.contains("SIGN")
        }.toSet()
    )

    // Reverse mapping: Material -> Category
    private val materialToCategory = mutableMapOf<Material, String>()

    // Custom materials added by config (direct material names)
    private val customMaterialLimits = mutableMapOf<Material, Int>()

    init {
        // Build reverse mapping for predefined categories
        blockCategories.forEach { (category, materials) ->
            materials.forEach { material ->
                materialToCategory[material] = category
            }
        }

        // Load custom material limits from config
        loadCustomMaterialLimits()

        // Register events
        Bukkit.getPluginManager().registerEvents(this, plugin)

        // Start cache cleanup task (every 2 minutes)
        cacheCleanupTask = SchedulerUtil.runAsyncTimer(2400L, 2400L, Runnable {
            radiusCountCache.clear()
            debug("Block count cache cleared")
        })

        plugin.logger.info("Block Limiter initialized (radius: ${config.radius} blocks)")
    }

    fun unregister() {
        HandlerList.unregisterAll(this)
        SchedulerUtil.cancelTask(cacheCleanupTask)
        cacheCleanupTask = null
        scope.cancel()
        radiusCountCache.clear()
        customMaterialLimits.clear()
    }

    /**
     * Load custom material limits from config
     * If a config key is not a predefined category, try to parse it as a Material name
     */
    private fun loadCustomMaterialLimits() {
        customMaterialLimits.clear()

        for ((key, limit) in config.limits) {
            // Skip if it's a predefined category
            if (blockCategories.containsKey(key)) continue
            // Skip entity categories
            if (key in listOf("armor-stands", "item-frames", "glow-item-frames", "paintings")) continue

            // Try to parse as Material name
            val materialName = key.uppercase().replace("-", "_")
            try {
                val material = Material.valueOf(materialName)
                customMaterialLimits[material] = limit
                debug("Loaded custom material limit: $material = $limit")
            } catch (e: IllegalArgumentException) {
                plugin.logger.warning("[BlockLimiter] Unknown material or category in config: $key")
            }
        }

        if (customMaterialLimits.isNotEmpty()) {
            plugin.logger.info("Block Limiter loaded ${customMaterialLimits.size} custom material limits")
        }
    }

    // ════════════════════════════════════════════════════════════
    // BLOCK PLACE EVENT
    // ════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (!config.enabled) return
        if (!config.worlds.contains(event.block.world.name)) return
        if (event.player.hasPermission(config.bypassPermission)) return

        val material = event.block.type
        val category = materialToCategory[material]

        // Check specific category limit (predefined groups like "hoppers", "pistons")
        if (category != null) {
            val limit = config.limits[category] ?: -1
            if (limit != -1) {
                val currentCount = getBlockCountInRadius(event.block.location, category)

                if (currentCount >= limit) {
                    event.isCancelled = true
                    sendDenyMessage(event.player, category, limit, currentCount)
                    debug("Blocked ${event.player.name} from placing $category at ${formatLocation(event.block.location)} (count: $currentCount, limit: $limit)")
                    return
                }
            }
        }

        // Check custom material limit (direct material names like "TNT", "SPAWNER")
        val customLimit = customMaterialLimits[material]
        if (customLimit != null && customLimit != -1) {
            val currentCount = getSpecificBlockCountInRadius(event.block.location, material)

            if (currentCount >= customLimit) {
                event.isCancelled = true
                sendDenyMessage(event.player, material.name.lowercase().replace("_", " "), customLimit, currentCount)
                debug("Blocked ${event.player.name} from placing ${material.name} at ${formatLocation(event.block.location)} (custom limit count: $currentCount, limit: $customLimit)")
                return
            }
        }

        // Check global limit if enabled
        if (config.globalLimitsEnabled) {
            val globalLimit = getGlobalLimitForMaterial(material)
            if (globalLimit != -1) {
                val currentCount = getSpecificBlockCountInRadius(event.block.location, material)

                if (currentCount >= globalLimit) {
                    event.isCancelled = true
                    sendDenyMessage(event.player, material.name.lowercase().replace("_", " "), globalLimit, currentCount)
                    debug("Blocked ${event.player.name} from placing ${material.name} at ${formatLocation(event.block.location)} (global count: $currentCount, limit: $globalLimit)")
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════
    // ENTITY PLACE EVENTS (Armor Stands, Item Frames, Paintings)
    // ════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onHangingPlace(event: HangingPlaceEvent) {
        if (!config.enabled) return
        val player = event.player ?: return
        if (!config.worlds.contains(event.entity.world.name)) return
        if (player.hasPermission(config.bypassPermission)) return

        val category = when (event.entity) {
            is ItemFrame -> {
                if (event.entity.type == EntityType.GLOW_ITEM_FRAME) "glow-item-frames" else "item-frames"
            }
            is Painting -> "paintings"
            else -> return
        }

        val limit = config.limits[category] ?: return
        if (limit == -1) return

        val currentCount = getEntityCountInRadius(event.entity.location, category)

        if (currentCount >= limit) {
            event.isCancelled = true
            sendDenyMessage(player, category, limit, currentCount)
            debug("Blocked ${player.name} from placing $category at ${formatLocation(event.entity.location)} (count: $currentCount, limit: $limit)")
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onArmorStandInteract(event: PlayerInteractEvent) {
        if (!config.enabled) return
        val item = event.item ?: return
        if (item.type != Material.ARMOR_STAND) return
        if (!config.worlds.contains(event.player.world.name)) return
        if (event.player.hasPermission(config.bypassPermission)) return

        val clickedBlock = event.clickedBlock ?: return
        val category = "armor-stands"
        val limit = config.limits[category] ?: return
        if (limit == -1) return

        val currentCount = getEntityCountInRadius(clickedBlock.location, category)

        if (currentCount >= limit) {
            event.isCancelled = true
            sendDenyMessage(event.player, category, limit, currentCount)
            debug("Blocked ${event.player.name} from placing armor stand at ${formatLocation(clickedBlock.location)} (count: $currentCount, limit: $limit)")
        }
    }

    // ════════════════════════════════════════════════════════════
    // RADIUS-BASED COUNT METHODS
    // ════════════════════════════════════════════════════════════

    private fun getBlockCountInRadius(center: Location, category: String): Int {
        val materials = blockCategories[category] ?: return 0
        val radius = config.radius
        val world = center.world ?: return 0

        var count = 0
        val centerX = center.blockX
        val centerY = center.blockY
        val centerZ = center.blockZ
        val radiusSquared = radius * radius

        // Scan within radius (optimized: only scan within bounding box)
        for (x in (centerX - radius)..(centerX + radius)) {
            for (z in (centerZ - radius)..(centerZ + radius)) {
                // Check horizontal distance first (faster)
                val dx = x - centerX
                val dz = z - centerZ
                if (dx * dx + dz * dz > radiusSquared) continue

                for (y in maxOf(world.minHeight, centerY - radius)..minOf(world.maxHeight - 1, centerY + radius)) {
                    val dy = y - centerY
                    // Full 3D distance check
                    if (dx * dx + dy * dy + dz * dz <= radiusSquared) {
                        val block = world.getBlockAt(x, y, z)
                        if (block.type in materials) {
                            count++
                        }
                    }
                }
            }
        }

        return count
    }

    private fun getSpecificBlockCountInRadius(center: Location, material: Material): Int {
        val radius = config.radius
        val world = center.world ?: return 0

        var count = 0
        val centerX = center.blockX
        val centerY = center.blockY
        val centerZ = center.blockZ
        val radiusSquared = radius * radius

        for (x in (centerX - radius)..(centerX + radius)) {
            for (z in (centerZ - radius)..(centerZ + radius)) {
                val dx = x - centerX
                val dz = z - centerZ
                if (dx * dx + dz * dz > radiusSquared) continue

                for (y in maxOf(world.minHeight, centerY - radius)..minOf(world.maxHeight - 1, centerY + radius)) {
                    val dy = y - centerY
                    if (dx * dx + dy * dy + dz * dz <= radiusSquared) {
                        val block = world.getBlockAt(x, y, z)
                        if (block.type == material) {
                            count++
                        }
                    }
                }
            }
        }

        return count
    }

    private fun getEntityCountInRadius(center: Location, category: String): Int {
        val radius = config.radius.toDouble()
        val world = center.world ?: return 0

        return when (category) {
            "armor-stands" -> world.getNearbyEntities(center, radius, radius, radius)
                .count { it is ArmorStand }
            "item-frames" -> world.getNearbyEntities(center, radius, radius, radius)
                .count { it is ItemFrame && it.type != EntityType.GLOW_ITEM_FRAME }
            "glow-item-frames" -> world.getNearbyEntities(center, radius, radius, radius)
                .count { it.type == EntityType.GLOW_ITEM_FRAME }
            "paintings" -> world.getNearbyEntities(center, radius, radius, radius)
                .count { it is Painting }
            else -> 0
        }
    }

    private fun getGlobalLimitForMaterial(material: Material): Int {
        val materialName = material.name.lowercase()

        // Check specific global limits first
        for ((category, limit) in config.globalLimits) {
            when (category) {
                "stone-variants" -> {
                    if (materialName.contains("stone") || materialName.contains("cobblestone") ||
                        materialName.contains("granite") || materialName.contains("diorite") ||
                        materialName.contains("andesite") || materialName.contains("deepslate") ||
                        materialName.contains("tuff") || materialName.contains("calcite")) {
                        return limit
                    }
                }
                "wood-variants" -> {
                    if (materialName.contains("_log") || materialName.contains("_wood") ||
                        materialName.contains("_plank") || materialName.contains("stripped_")) {
                        return limit
                    }
                }
                "terracotta" -> {
                    if (materialName.contains("terracotta")) {
                        return limit
                    }
                }
                "concrete" -> {
                    if (materialName.contains("concrete")) {
                        return limit
                    }
                }
                "glass" -> {
                    if (materialName.contains("glass")) {
                        return limit
                    }
                }
                "wool" -> {
                    if (materialName.contains("wool")) {
                        return limit
                    }
                }
            }
        }

        // Return default limit for other blocks
        return config.globalDefaultLimit
    }

    // ════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ════════════════════════════════════════════════════════════

    private fun sendDenyMessage(player: Player, category: String, limit: Int, current: Int) {
        val message = config.denyMessage
            .replace("{block}", formatCategoryName(category))
            .replace("{limit}", limit.toString())
            .replace("{current}", current.toString())
            .replace("{radius}", config.radius.toString())

        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message))
    }

    private fun formatCategoryName(category: String): String {
        return category.replace("-", " ").split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercase() }
        }
    }

    private fun formatLocation(loc: Location): String {
        return "${loc.world?.name}: ${loc.blockX}, ${loc.blockY}, ${loc.blockZ}"
    }

    private fun debug(message: String) {
        if (config.debug) {
            plugin.logger.info("[BlockLimiter] $message")
        }
    }
}
