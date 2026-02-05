package dev.srcodex.serverbooster.chunk

import dev.srcodex.serverbooster.ServerBoosterPlugin
import dev.srcodex.serverbooster.config.ChunkBlockLimitsConfig
import dev.srcodex.serverbooster.util.SchedulerUtil
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
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.hanging.HangingPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.world.WorldLoadEvent
import org.bukkit.event.world.WorldUnloadEvent
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Limits the number of specific block types within a radius.
 *
 * IMPORTANT: This system only tracks blocks PLACED BY PLAYERS, not natural world blocks.
 * This prevents issues where natural terrain (dirt, stone, etc.) would count against limits.
 *
 * PERSISTENCE: Block tracking data is saved to disk and survives server restarts.
 * - Data is saved asynchronously every 5 minutes (only if changes occurred)
 * - Data is saved on server shutdown
 * - One file per world for efficient loading
 * - Compact format: one line per block (x,y,z,MATERIAL)
 */
class ChunkBlockLimiter(private val plugin: ServerBoosterPlugin) : Listener {

    private val config: ChunkBlockLimitsConfig
        get() = plugin.configManager.chunkBlockLimitsConfig

    // ════════════════════════════════════════════════════════════
    // PLAYER-PLACED BLOCK TRACKING (Per-World)
    // ════════════════════════════════════════════════════════════

    /**
     * Tracks blocks placed by players, organized by world.
     * Outer key: World name
     * Inner key: "x:y:z" -> Material
     */
    private val worldBlockData = ConcurrentHashMap<String, ConcurrentHashMap<String, Material>>()

    /**
     * Tracks which worlds have unsaved changes (dirty flag per world)
     */
    private val dirtyWorlds = ConcurrentHashMap.newKeySet<String>()

    /**
     * Data directory for persistence
     */
    private val dataFolder: File by lazy {
        File(plugin.dataFolder, "block_tracking").also { it.mkdirs() }
    }

    // Materials that can generate naturally - these need tracking for global limits
    private val naturalMaterials = setOf(
        // Stones
        Material.STONE, Material.GRANITE, Material.DIORITE, Material.ANDESITE,
        Material.DEEPSLATE, Material.TUFF, Material.CALCITE, Material.COBBLESTONE,
        Material.MOSSY_COBBLESTONE, Material.COBBLED_DEEPSLATE,

        // Dirt variants
        Material.DIRT, Material.GRASS_BLOCK, Material.PODZOL, Material.MYCELIUM,
        Material.COARSE_DIRT, Material.ROOTED_DIRT, Material.MUD, Material.MUDDY_MANGROVE_ROOTS,

        // Sand/Gravel
        Material.SAND, Material.RED_SAND, Material.GRAVEL, Material.CLAY,
        Material.SOUL_SAND, Material.SOUL_SOIL,

        // Ores (can be placed back)
        Material.COAL_ORE, Material.IRON_ORE, Material.GOLD_ORE, Material.DIAMOND_ORE,
        Material.EMERALD_ORE, Material.LAPIS_ORE, Material.REDSTONE_ORE, Material.COPPER_ORE,
        Material.DEEPSLATE_COAL_ORE, Material.DEEPSLATE_IRON_ORE, Material.DEEPSLATE_GOLD_ORE,
        Material.DEEPSLATE_DIAMOND_ORE, Material.DEEPSLATE_EMERALD_ORE, Material.DEEPSLATE_LAPIS_ORE,
        Material.DEEPSLATE_REDSTONE_ORE, Material.DEEPSLATE_COPPER_ORE, Material.NETHER_GOLD_ORE,
        Material.NETHER_QUARTZ_ORE, Material.ANCIENT_DEBRIS,

        // Nether
        Material.NETHERRACK, Material.BASALT, Material.BLACKSTONE, Material.MAGMA_BLOCK,
        Material.GLOWSTONE, Material.CRIMSON_NYLIUM, Material.WARPED_NYLIUM,

        // End
        Material.END_STONE, Material.OBSIDIAN,

        // Logs/Wood (trees)
        Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG,
        Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG,
        Material.CRIMSON_STEM, Material.WARPED_STEM,

        // Leaves
        Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES, Material.JUNGLE_LEAVES,
        Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES, Material.MANGROVE_LEAVES, Material.CHERRY_LEAVES,
        Material.AZALEA_LEAVES, Material.FLOWERING_AZALEA_LEAVES,

        // Ice/Snow
        Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE, Material.SNOW_BLOCK, Material.POWDER_SNOW,

        // Terracotta (mesa biome)
        Material.TERRACOTTA, Material.WHITE_TERRACOTTA, Material.ORANGE_TERRACOTTA,
        Material.YELLOW_TERRACOTTA, Material.BROWN_TERRACOTTA, Material.RED_TERRACOTTA,
        Material.LIGHT_GRAY_TERRACOTTA,

        // Misc natural
        Material.SANDSTONE, Material.RED_SANDSTONE, Material.PRISMARINE, Material.DRIPSTONE_BLOCK,
        Material.POINTED_DRIPSTONE, Material.MOSS_BLOCK, Material.SCULK, Material.AMETHYST_BLOCK
    )

    // Block category mappings (these don't generate naturally)
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

    // All materials that belong to a category (these don't need player tracking)
    private val categorizedMaterials = mutableSetOf<Material>()

    // Tasks
    private var autoSaveTask: Any? = null
    private var cleanupTask: Any? = null

    // Flag to prevent saving during shutdown if already saved
    private val isShuttingDown = AtomicBoolean(false)

    init {
        // Build reverse mapping for predefined categories
        blockCategories.forEach { (category, materials) ->
            materials.forEach { material ->
                materialToCategory[material] = category
                categorizedMaterials.add(material)
            }
        }

        // Load custom material limits from config
        loadCustomMaterialLimits()

        // Load existing data for all configured worlds
        loadAllWorldData()

        // Register events
        Bukkit.getPluginManager().registerEvents(this, plugin)

        // Auto-save task: every 5 minutes (6000 ticks), only save dirty worlds
        autoSaveTask = SchedulerUtil.runAsyncTimer(6000L, 6000L) {
            saveAllDirtyWorlds()
        }

        // Cleanup task: every 30 minutes, remove invalid blocks (runs on main thread for world access)
        cleanupTask = SchedulerUtil.runTaskTimer(36000L, 36000L) {
            cleanupInvalidBlocks()
        }

        val totalBlocks = worldBlockData.values.sumOf { it.size }
        plugin.logger.info("[BlockLimiter] Initialized (radius: ${config.radius} blocks)")
        plugin.logger.info("[BlockLimiter] Loaded $totalBlocks tracked blocks from ${worldBlockData.size} worlds")
    }

    fun unregister() {
        isShuttingDown.set(true)

        HandlerList.unregisterAll(this)
        SchedulerUtil.cancelTask(autoSaveTask)
        SchedulerUtil.cancelTask(cleanupTask)
        autoSaveTask = null
        cleanupTask = null

        // Final save (synchronous on shutdown)
        saveAllWorldsSync()

        worldBlockData.clear()
        dirtyWorlds.clear()
        customMaterialLimits.clear()
    }

    // ════════════════════════════════════════════════════════════
    // PERSISTENCE - SAVE/LOAD
    // ════════════════════════════════════════════════════════════

    /**
     * Get the data file for a specific world
     */
    private fun getWorldFile(worldName: String): File {
        // Sanitize world name for filename
        val safeName = worldName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(dataFolder, "$safeName.dat")
    }

    /**
     * Load data for all configured worlds
     */
    private fun loadAllWorldData() {
        for (worldName in config.worlds) {
            loadWorldData(worldName)
        }
    }

    /**
     * Load block tracking data for a specific world
     */
    private fun loadWorldData(worldName: String) {
        val file = getWorldFile(worldName)
        if (!file.exists()) {
            worldBlockData[worldName] = ConcurrentHashMap()
            return
        }

        val blocks = ConcurrentHashMap<String, Material>()
        var loadedCount = 0
        var errorCount = 0

        try {
            BufferedReader(FileReader(file)).use { reader ->
                reader.lineSequence().forEach { line ->
                    if (line.isBlank()) return@forEach

                    try {
                        // Format: x,y,z,MATERIAL_NAME
                        val parts = line.split(',', limit = 4)
                        if (parts.size == 4) {
                            val x = parts[0].toInt()
                            val y = parts[1].toInt()
                            val z = parts[2].toInt()
                            val material = Material.valueOf(parts[3])

                            val key = "$x:$y:$z"
                            blocks[key] = material
                            loadedCount++
                        }
                    } catch (e: Exception) {
                        errorCount++
                    }
                }
            }

            worldBlockData[worldName] = blocks
            debug("Loaded $loadedCount blocks for world $worldName" +
                    if (errorCount > 0) " ($errorCount errors)" else "")

        } catch (e: Exception) {
            plugin.logger.warning("[BlockLimiter] Failed to load data for world $worldName: ${e.message}")
            worldBlockData[worldName] = ConcurrentHashMap()
        }
    }

    /**
     * Save all worlds that have changes (async)
     */
    private fun saveAllDirtyWorlds() {
        if (dirtyWorlds.isEmpty()) return

        val worldsToSave = dirtyWorlds.toList()
        dirtyWorlds.clear()

        for (worldName in worldsToSave) {
            saveWorldData(worldName)
        }
    }

    /**
     * Save all worlds synchronously (for shutdown)
     */
    private fun saveAllWorldsSync() {
        val totalSaved = worldBlockData.entries.sumOf { (worldName, _) ->
            saveWorldData(worldName)
        }
        plugin.logger.info("[BlockLimiter] Saved $totalSaved tracked blocks across ${worldBlockData.size} worlds")
    }

    /**
     * Save block tracking data for a specific world
     * Returns number of blocks saved
     */
    private fun saveWorldData(worldName: String): Int {
        val blocks = worldBlockData[worldName] ?: return 0
        if (blocks.isEmpty()) {
            // Delete file if no blocks
            val file = getWorldFile(worldName)
            if (file.exists()) file.delete()
            return 0
        }

        val file = getWorldFile(worldName)
        var savedCount = 0

        try {
            // Write to temp file first, then rename (atomic operation)
            val tempFile = File(file.parentFile, "${file.name}.tmp")

            BufferedWriter(FileWriter(tempFile)).use { writer ->
                for ((key, material) in blocks) {
                    val parts = key.split(':')
                    if (parts.size == 3) {
                        // Format: x,y,z,MATERIAL_NAME
                        writer.write("${parts[0]},${parts[1]},${parts[2]},${material.name}")
                        writer.newLine()
                        savedCount++
                    }
                }
            }

            // Atomic rename
            if (file.exists()) file.delete()
            tempFile.renameTo(file)

            debug("Saved $savedCount blocks for world $worldName")

        } catch (e: Exception) {
            plugin.logger.warning("[BlockLimiter] Failed to save data for world $worldName: ${e.message}")
        }

        return savedCount
    }

    // ════════════════════════════════════════════════════════════
    // WORLD EVENTS - Load/Unload data with worlds
    // ════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR)
    fun onWorldLoad(event: WorldLoadEvent) {
        val worldName = event.world.name
        if (config.worlds.contains(worldName) && !worldBlockData.containsKey(worldName)) {
            SchedulerUtil.runAsync {
                loadWorldData(worldName)
                debug("Loaded data for world $worldName on world load")
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onWorldUnload(event: WorldUnloadEvent) {
        val worldName = event.world.name
        if (worldBlockData.containsKey(worldName)) {
            // Save before unloading
            SchedulerUtil.runAsync {
                saveWorldData(worldName)
                debug("Saved and unloaded data for world $worldName")
            }
        }
    }

    // ════════════════════════════════════════════════════════════
    // BLOCK TRACKING HELPERS
    // ════════════════════════════════════════════════════════════

    private fun createBlockKey(x: Int, y: Int, z: Int): String = "$x:$y:$z"

    private fun trackPlayerPlacedBlock(worldName: String, x: Int, y: Int, z: Int, material: Material) {
        val blocks = worldBlockData.getOrPut(worldName) { ConcurrentHashMap() }
        val key = createBlockKey(x, y, z)
        blocks[key] = material
        dirtyWorlds.add(worldName)
        debug("Tracked: $material at $worldName:$key (world total: ${blocks.size})")
    }

    private fun untrackBlock(worldName: String, x: Int, y: Int, z: Int) {
        val blocks = worldBlockData[worldName] ?: return
        val key = createBlockKey(x, y, z)
        val removed = blocks.remove(key)
        if (removed != null) {
            dirtyWorlds.add(worldName)
            debug("Untracked block at $worldName:$key (world total: ${blocks.size})")
        }
    }

    /**
     * Load custom material limits from config
     */
    private fun loadCustomMaterialLimits() {
        customMaterialLimits.clear()

        for ((key, limit) in config.limits) {
            if (blockCategories.containsKey(key)) continue
            if (key in listOf("armor-stands", "item-frames", "glow-item-frames", "paintings")) continue

            val materialName = key.uppercase().replace("-", "_")
            try {
                val material = Material.valueOf(materialName)
                customMaterialLimits[material] = limit
                categorizedMaterials.add(material)
                debug("Loaded custom material limit: $material = $limit")
            } catch (e: IllegalArgumentException) {
                plugin.logger.warning("[BlockLimiter] Unknown material or category in config: $key")
            }
        }

        if (customMaterialLimits.isNotEmpty()) {
            plugin.logger.info("[BlockLimiter] Loaded ${customMaterialLimits.size} custom material limits")
        }
    }

    /**
     * Check if a material needs player tracking for limits.
     */
    private fun needsPlayerTracking(material: Material): Boolean {
        if (categorizedMaterials.contains(material)) return false
        if (naturalMaterials.contains(material)) return true
        return material.isBlock && material.isSolid
    }

    /**
     * Cleanup blocks that no longer exist in the world.
     * Only removes blocks that are now AIR (destroyed), not blocks that changed material.
     * This prevents false positives from natural block changes (grass→dirt, etc.)
     */
    private fun cleanupInvalidBlocks() {
        var totalRemoved = 0
        var totalChecked = 0

        for ((worldName, blocks) in worldBlockData) {
            val world = Bukkit.getWorld(worldName) ?: continue
            val toRemove = mutableListOf<String>()

            for ((key, _) in blocks) {
                val parts = key.split(':')
                if (parts.size != 3) {
                    toRemove.add(key)
                    continue
                }

                val x = parts[0].toIntOrNull() ?: continue
                val y = parts[1].toIntOrNull() ?: continue
                val z = parts[2].toIntOrNull() ?: continue

                // Only check loaded chunks
                val chunkX = x shr 4
                val chunkZ = z shr 4
                if (!world.isChunkLoaded(chunkX, chunkZ)) continue

                totalChecked++
                val block = world.getBlockAt(x, y, z)

                // Only remove if block is now AIR (was destroyed)
                // Don't remove if material changed (grass→dirt, etc.)
                if (block.type == Material.AIR || block.type == Material.CAVE_AIR || block.type == Material.VOID_AIR) {
                    toRemove.add(key)
                }
            }

            if (toRemove.isNotEmpty()) {
                toRemove.forEach { blocks.remove(it) }
                dirtyWorlds.add(worldName)
                totalRemoved += toRemove.size
            }
        }

        if (totalRemoved > 0 || config.debug) {
            debug("Cleanup: checked $totalChecked blocks, removed $totalRemoved destroyed blocks")
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
        val block = event.block
        val location = block.location
        val worldName = block.world.name
        val category = materialToCategory[material]

        // Check specific category limit (hoppers, pistons, etc.)
        if (category != null) {
            val limit = config.limits[category] ?: -1
            if (limit != -1) {
                val currentCount = getBlockCountInRadius(location, category)

                if (currentCount >= limit) {
                    event.isCancelled = true
                    sendDenyMessage(event.player, category, limit, currentCount)
                    debug("Blocked ${event.player.name} from placing $category (count: $currentCount, limit: $limit)")
                    return
                }
            }
        }

        // Check custom material limit
        val customLimit = customMaterialLimits[material]
        if (customLimit != null && customLimit != -1) {
            val currentCount = getSpecificBlockCountInRadius(location, material)

            if (currentCount >= customLimit) {
                event.isCancelled = true
                sendDenyMessage(event.player, material.name.lowercase().replace("_", " "), customLimit, currentCount)
                debug("Blocked ${event.player.name} from placing ${material.name} (custom limit: $currentCount/$customLimit)")
                return
            }
        }

        // Check global limit - only count PLAYER-PLACED blocks
        if (config.globalLimitsEnabled) {
            val globalLimit = getGlobalLimitForMaterial(material)
            if (globalLimit != -1) {
                val currentCount = getPlayerPlacedBlockCountInRadius(worldName, block.x, block.y, block.z, material)

                if (currentCount >= globalLimit) {
                    event.isCancelled = true
                    sendDenyMessage(event.player, material.name.lowercase().replace("_", " "), globalLimit, currentCount)
                    debug("Blocked ${event.player.name} from placing ${material.name} (global limit: $currentCount/$globalLimit)")
                    return
                }
            }
        }

        // Placement allowed - track if needed
        if (needsPlayerTracking(material)) {
            trackPlayerPlacedBlock(worldName, block.x, block.y, block.z, material)
        }
    }

    // ════════════════════════════════════════════════════════════
    // BLOCK BREAK EVENT
    // ════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (!config.enabled) return
        val block = event.block
        untrackBlock(block.world.name, block.x, block.y, block.z)
    }

    // ════════════════════════════════════════════════════════════
    // ENTITY PLACE EVENTS
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
            debug("Blocked ${player.name} from placing $category (count: $currentCount, limit: $limit)")
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
            debug("Blocked ${event.player.name} from placing armor stand (count: $currentCount, limit: $limit)")
        }
    }

    // ════════════════════════════════════════════════════════════
    // RADIUS-BASED COUNT METHODS
    // ════════════════════════════════════════════════════════════

    /**
     * Count blocks in radius by category (for non-natural blocks)
     */
    private fun getBlockCountInRadius(center: Location, category: String): Int {
        val materials = blockCategories[category] ?: return 0
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
                        if (block.type in materials) {
                            count++
                        }
                    }
                }
            }
        }

        return count
    }

    /**
     * Count specific material in radius
     */
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

    /**
     * Count ONLY player-placed blocks in radius (for global limits)
     */
    private fun getPlayerPlacedBlockCountInRadius(worldName: String, centerX: Int, centerY: Int, centerZ: Int, material: Material): Int {
        val blocks = worldBlockData[worldName] ?: return 0
        val radius = config.radius
        val radiusSquared = radius * radius

        var count = 0

        for (x in (centerX - radius)..(centerX + radius)) {
            for (z in (centerZ - radius)..(centerZ + radius)) {
                val dx = x - centerX
                val dz = z - centerZ
                if (dx * dx + dz * dz > radiusSquared) continue

                for (y in (centerY - radius)..(centerY + radius)) {
                    val dy = y - centerY
                    if (dx * dx + dy * dy + dz * dz <= radiusSquared) {
                        val key = "$x:$y:$z"
                        val trackedMaterial = blocks[key]
                        if (trackedMaterial == material) {
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
                "dirt-variants" -> {
                    if (materialName.contains("dirt") || materialName == "grass_block" ||
                        materialName == "podzol" || materialName == "mycelium") {
                        return limit
                    }
                }
                "sand-variants" -> {
                    if (materialName.contains("sand") && !materialName.contains("sandstone")) {
                        return limit
                    }
                }
            }
        }

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

    private fun debug(message: String) {
        if (config.debug) {
            plugin.logger.info("[BlockLimiter] $message")
        }
    }

    // ════════════════════════════════════════════════════════════
    // PUBLIC API
    // ════════════════════════════════════════════════════════════

    /**
     * Get total tracked blocks across all worlds
     */
    fun getTrackedBlockCount(): Int = worldBlockData.values.sumOf { it.size }

    /**
     * Get tracked blocks per world
     */
    fun getTrackedBlockCountPerWorld(): Map<String, Int> {
        return worldBlockData.mapValues { it.value.size }
    }

    /**
     * Get breakdown of tracked blocks by material
     */
    fun getTrackedBlockStats(): Map<Material, Int> {
        val stats = mutableMapOf<Material, Int>()
        for (blocks in worldBlockData.values) {
            for (material in blocks.values) {
                stats[material] = stats.getOrDefault(material, 0) + 1
            }
        }
        return stats
    }

    /**
     * Force save all data (for admin commands)
     */
    fun forceSave() {
        SchedulerUtil.runAsync {
            saveAllWorldsSync()
        }
    }
}
