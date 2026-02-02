package dev.srcodex.serverbooster.detection

import dev.srcodex.serverbooster.ServerBoosterPlugin
import dev.srcodex.serverbooster.util.SchedulerUtil
import kotlinx.coroutines.*
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.CreatureSpawner
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap

/**
 * Detection manager for finding redstone mechanisms and spawners.
 * Uses coroutines for optimized async processing.
 */
class DetectionManager(private val plugin: ServerBoosterPlugin) {

    companion object {
        const val DEFAULT_RADIUS = 50
        const val MAX_RADIUS = 50
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // ════════════════════════════════════════════════════════════
    // DATA CLASSES
    // ════════════════════════════════════════════════════════════

    data class SpawnerInfo(
        val location: Location,
        val entityType: EntityType,
        val delay: Int,
        val nearbyEntityCount: Int,
        val isActive: Boolean,
        val nearbyPlayers: List<PlayerProximity>
    ) {
        val worldName: String get() = location.world?.name ?: "unknown"
        val x: Int get() = location.blockX
        val y: Int get() = location.blockY
        val z: Int get() = location.blockZ
    }

    data class RedstoneInfo(
        val location: Location,
        val type: Material,
        val isPowered: Boolean,
        val density: Int, // How many redstone components nearby
        val nearbyPlayers: List<PlayerProximity>
    ) {
        val worldName: String get() = location.world?.name ?: "unknown"
        val x: Int get() = location.blockX
        val y: Int get() = location.blockY
        val z: Int get() = location.blockZ
    }

    data class PlayerProximity(
        val playerName: String,
        val distance: Double
    )

    data class DetectionResult<T>(
        val items: List<T>,
        val playersScanned: Int,
        val scanTimeMs: Long
    )

    // ════════════════════════════════════════════════════════════
    // REDSTONE MATERIALS
    // ════════════════════════════════════════════════════════════

    private val redstoneMaterials = setOf(
        Material.REDSTONE_WIRE,
        Material.REDSTONE_TORCH,
        Material.REDSTONE_WALL_TORCH,
        Material.REDSTONE_BLOCK,
        Material.REDSTONE_LAMP,
        Material.REPEATER,
        Material.COMPARATOR,
        Material.OBSERVER,
        Material.PISTON,
        Material.STICKY_PISTON,
        Material.PISTON_HEAD,
        Material.MOVING_PISTON,
        Material.DROPPER,
        Material.DISPENSER,
        Material.HOPPER,
        Material.DAYLIGHT_DETECTOR,
        Material.LEVER,
        Material.TRIPWIRE_HOOK,
        Material.TRIPWIRE,
        Material.TARGET
    )

    // High-activity redstone components (potential lag sources)
    private val highActivityRedstone = setOf(
        Material.OBSERVER,
        Material.PISTON,
        Material.STICKY_PISTON,
        Material.HOPPER,
        Material.COMPARATOR,
        Material.REPEATER,
        Material.DROPPER,
        Material.DISPENSER
    )

    // Non-tile entity high-activity redstone (for sample scanning)
    private val nonTileHighActivityRedstone = setOf(
        Material.OBSERVER,
        Material.PISTON,
        Material.STICKY_PISTON,
        Material.PISTON_HEAD,
        Material.MOVING_PISTON,
        Material.COMPARATOR,
        Material.REPEATER
    )

    // ════════════════════════════════════════════════════════════
    // SPAWNER DETECTION
    // ════════════════════════════════════════════════════════════

    /**
     * Detect spawners near all online players using coroutines
     */
    suspend fun detectSpawners(
        entityTypeFilter: EntityType? = null,
        radius: Int = DEFAULT_RADIUS
    ): DetectionResult<SpawnerInfo> {
        val startTime = System.currentTimeMillis()
        val effectiveRadius = radius.coerceIn(1, MAX_RADIUS)

        // Get data from main thread
        val scanData = runOnMainThread {
            val players = Bukkit.getOnlinePlayers().toList()
            val chunksToScan = mutableSetOf<Chunk>()

            players.forEach { player ->
                val playerChunkX = player.location.blockX shr 4
                val playerChunkZ = player.location.blockZ shr 4
                val chunkRadius = (effectiveRadius / 16) + 1

                for (dx in -chunkRadius..chunkRadius) {
                    for (dz in -chunkRadius..chunkRadius) {
                        val chunk = player.world.getChunkAt(playerChunkX + dx, playerChunkZ + dz)
                        if (chunk.isLoaded) {
                            chunksToScan.add(chunk)
                        }
                    }
                }
            }

            Triple(players, chunksToScan, players.size)
        }

        val (players, chunksToScan, playerCount) = scanData
        val foundSpawners = ConcurrentHashMap<String, SpawnerInfo>()

        // Process chunks on main thread (tile entities must be accessed on main thread)
        runOnMainThread {
            for (chunk in chunksToScan) {
                chunk.tileEntities
                    .asSequence()
                    .filterIsInstance<CreatureSpawner>()
                    .filter { spawner ->
                        entityTypeFilter == null || spawner.spawnedType == entityTypeFilter
                    }
                    .forEach { spawner ->
                        val loc = spawner.location
                        val key = "${loc.world?.name}:${loc.blockX}:${loc.blockY}:${loc.blockZ}"

                        val nearbyPlayers = players
                            .filter { it.world == loc.world }
                            .mapNotNull { player ->
                                try {
                                    val dist = player.location.distance(loc)
                                    if (dist <= effectiveRadius) {
                                        PlayerProximity(player.name, dist)
                                    } else null
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            .sortedBy { it.distance }

                        if (nearbyPlayers.isNotEmpty()) {
                            val nearbyEntityCount = try {
                                loc.getNearbyEntities(16.0, 16.0, 16.0).size
                            } catch (e: Exception) { 0 }

                            val isActive = nearbyPlayers.any { it.distance <= 16 }

                            foundSpawners[key] = SpawnerInfo(
                                location = loc,
                                entityType = spawner.spawnedType ?: EntityType.PIG,
                                delay = spawner.delay,
                                nearbyEntityCount = nearbyEntityCount,
                                isActive = isActive,
                                nearbyPlayers = nearbyPlayers
                            )
                        }
                    }
            }
        }

        val sortedSpawners = foundSpawners.values
            .sortedWith(compareBy({ !it.isActive }, { it.nearbyPlayers.firstOrNull()?.distance ?: Double.MAX_VALUE }))

        return DetectionResult(
            items = sortedSpawners,
            playersScanned = playerCount,
            scanTimeMs = System.currentTimeMillis() - startTime
        )
    }

    /**
     * Detect spawners by entity type name
     */
    suspend fun detectSpawnersByType(entityTypeName: String, radius: Int = DEFAULT_RADIUS): DetectionResult<SpawnerInfo> {
        val entityType = try {
            EntityType.valueOf(entityTypeName.uppercase())
        } catch (e: IllegalArgumentException) {
            return DetectionResult(emptyList(), 0, 0)
        }
        return detectSpawners(entityType, radius)
    }

    // ════════════════════════════════════════════════════════════
    // REDSTONE DETECTION
    // ════════════════════════════════════════════════════════════

    /**
     * Detect redstone mechanisms near all online players using coroutines
     *
     * OPTIMIZED: Uses tile entities and snapshot scanning instead of iterating every block.
     * Previous implementation was O(n^3) scanning ALL Y levels which caused severe CPU issues.
     */
    suspend fun detectRedstone(radius: Int = 30): DetectionResult<RedstoneInfo> {
        val startTime = System.currentTimeMillis()
        val effectiveRadius = radius.coerceIn(1, 30) // Max 30 for performance

        // Get data from main thread
        val scanData = runOnMainThread {
            val players = Bukkit.getOnlinePlayers().toList()
            val chunksToScan = mutableSetOf<Chunk>()

            players.forEach { player ->
                val playerChunkX = player.location.blockX shr 4
                val playerChunkZ = player.location.blockZ shr 4
                val chunkRadius = (effectiveRadius / 16) + 1

                for (dx in -chunkRadius..chunkRadius) {
                    for (dz in -chunkRadius..chunkRadius) {
                        val chunk = player.world.getChunkAt(playerChunkX + dx, playerChunkZ + dz)
                        if (chunk.isLoaded) {
                            chunksToScan.add(chunk)
                        }
                    }
                }
            }

            Triple(players, chunksToScan, players.size)
        }

        val (players, chunksToScan, playerCount) = scanData
        val foundRedstone = ConcurrentHashMap<String, RedstoneInfo>()

        // OPTIMIZED: Scan chunks for redstone using tile entities first, then sample scan
        // This avoids the O(n^3) full block scan that was causing CPU issues
        runOnMainThread {
            for (chunk in chunksToScan) {
                val world = chunk.world

                // First: Check tile entities (hoppers, dispensers, droppers, etc.)
                // These are the main lag sources and are indexed by the server
                for (tileEntity in chunk.tileEntities) {
                    val block = tileEntity.block
                    if (block.type !in highActivityRedstone) continue

                    processRedstoneBlock(block, players, effectiveRadius, foundRedstone)
                }

                // Second: Sample scan for non-tile redstone (observers, pistons, etc.)
                // Only scan every 4th block vertically to reduce CPU usage significantly
                // Focus on typical build heights (y=0 to y=128 covers most player builds)
                val minY = maxOf(world.minHeight, -64)
                val maxY = minOf(world.maxHeight, 128)

                for (x in 0..15 step 2) {  // Sample every 2nd block horizontally
                    for (z in 0..15 step 2) {
                        for (y in minY until maxY step 4) {  // Sample every 4th block vertically
                            val block = chunk.getBlock(x, y, z)
                            if (block.type !in nonTileHighActivityRedstone) continue

                            processRedstoneBlock(block, players, effectiveRadius, foundRedstone)
                        }
                    }
                }
            }
        }

        val sortedRedstone = foundRedstone.values
            .sortedByDescending { it.density }
            .take(100) // Limit results

        return DetectionResult(
            items = sortedRedstone,
            playersScanned = playerCount,
            scanTimeMs = System.currentTimeMillis() - startTime
        )
    }

    /**
     * Process a single redstone block and add to results if near players
     */
    private fun processRedstoneBlock(
        block: org.bukkit.block.Block,
        players: List<Player>,
        effectiveRadius: Int,
        results: ConcurrentHashMap<String, RedstoneInfo>
    ) {
        val loc = block.location
        val key = "${loc.world?.name}:${loc.blockX}:${loc.blockY}:${loc.blockZ}"

        // Skip if already processed
        if (results.containsKey(key)) return

        val nearbyPlayers = players
            .filter { it.world == loc.world }
            .mapNotNull { player ->
                try {
                    val dist = player.location.distance(loc)
                    if (dist <= effectiveRadius) {
                        PlayerProximity(player.name, dist)
                    } else null
                } catch (e: Exception) {
                    null
                }
            }
            .sortedBy { it.distance }

        if (nearbyPlayers.isNotEmpty()) {
            // Simplified density check - only immediate neighbors
            var density = 0
            for (dx in -1..1) {
                for (dy in -1..1) {
                    for (dz in -1..1) {
                        try {
                            val nearby = loc.world?.getBlockAt(
                                loc.blockX + dx,
                                loc.blockY + dy,
                                loc.blockZ + dz
                            )
                            if (nearby != null && nearby.type in redstoneMaterials) {
                                density++
                            }
                        } catch (e: Exception) { }
                    }
                }
            }

            val isPowered = try {
                block.blockPower > 0 ||
                        block.type == Material.OBSERVER ||
                        block.type == Material.COMPARATOR
            } catch (e: Exception) { false }

            results[key] = RedstoneInfo(
                location = loc,
                type = block.type,
                isPowered = isPowered,
                density = density,
                nearbyPlayers = nearbyPlayers
            )
        }
    }

    // ════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ════════════════════════════════════════════════════════════

    /**
     * Run code on main thread and suspend until complete
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun <T> runOnMainThread(block: () -> T): T {
        if (Bukkit.isPrimaryThread()) {
            return block()
        }

        return suspendCancellableCoroutine { continuation ->
            SchedulerUtil.runTask {
                try {
                    val result = block()
                    continuation.resume(result) { }
                } catch (e: Exception) {
                    continuation.cancel(e)
                }
            }
        }
    }

    fun formatLocation(loc: Location): String {
        return "${loc.world?.name ?: "?"}: ${loc.blockX}, ${loc.blockY}, ${loc.blockZ}"
    }

    fun getTeleportCommand(loc: Location): String {
        return "/tp ${loc.blockX} ${loc.blockY} ${loc.blockZ}"
    }

    fun isValidEntityType(name: String): Boolean {
        return try {
            EntityType.valueOf(name.uppercase())
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    fun getCommonSpawnerTypes(): List<String> {
        return listOf(
            "zombie", "skeleton", "spider", "cave_spider", "creeper",
            "blaze", "silverfish", "magma_cube", "slime",
            "enderman", "piglin", "zombified_piglin", "wither_skeleton"
        )
    }

    fun shutdown() {
        scope.cancel()
    }
}
