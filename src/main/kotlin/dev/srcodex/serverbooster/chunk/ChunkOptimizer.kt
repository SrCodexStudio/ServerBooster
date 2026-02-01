package dev.srcodex.serverbooster.chunk

import dev.srcodex.serverbooster.ServerBoosterPlugin
import dev.srcodex.serverbooster.util.SchedulerUtil
import kotlinx.coroutines.*
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.World
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max

class ChunkOptimizer(private val plugin: ServerBoosterPlugin) {

    private val config get() = plugin.configManager.chunkOptimizerConfig

    // Store config values at initialization time (refreshed on reload when new instance is created)
    private val configuredWorlds: Set<String> = config.unloadChunksWorlds.toSet()
    private val unloadInterval: Long = config.unloadChunksInterval.toLong()

    private var unloadTask: Any? = null

    // Coroutine scope for async processing
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Batch size for unloading chunks per tick (prevents lag spikes)
    private val unloadBatchSize = 50

    // Track chunks that have been loaded for extended periods
    private val chunkLoadTimes = ConcurrentHashMap<String, Long>()

    init {
        startUnloadTask()
    }

    fun shutdown() {
        SchedulerUtil.cancelTask(unloadTask)
        scope.cancel()
        chunkLoadTimes.clear()
    }

    private fun startUnloadTask() {
        if (!config.unloadChunksEnabled) {
            plugin.logger.info("Chunk unloader is disabled in config")
            return
        }

        plugin.logger.info("Starting chunk unloader - interval: $unloadInterval ticks, worlds: $configuredWorlds")

        // Timer that triggers the unload process on main thread
        unloadTask = SchedulerUtil.runTaskTimer(
            20L,
            unloadInterval
        ) {
            // Run directly on main thread for accurate chunk data
            processChunkUnload()
        }
    }

    /**
     * Data class to hold chunk info for processing
     */
    private data class ChunkData(
        val chunk: Chunk,
        val x: Int,
        val z: Int,
        val isSpawnChunk: Boolean,
        val isForceLoaded: Boolean,
        val nearestPlayerDistance: Int
    )

    /**
     * Process chunk unloading on the main thread
     * Uses batching and yields to prevent lag
     */
    private fun processChunkUnload() {
        var totalUnloaded = 0

        for (world in Bukkit.getWorlds()) {
            if (!configuredWorlds.contains(world.name)) continue

            val loadedChunks = world.loadedChunks

            // Get view distance (in chunks)
            val viewDistance = Bukkit.getViewDistance()

            // Get player chunk positions
            val playerChunks = world.players.map { player ->
                val chunk = player.location.chunk
                Pair(chunk.x, chunk.z)
            }

            val chunksToProcess = mutableListOf<ChunkData>()

            for (chunk in loadedChunks) {
                val chunkX = chunk.x
                val chunkZ = chunk.z

                // Check if spawn chunk using proper detection
                val isSpawn = isSpawnChunk(chunk, world)

                // Check if force loaded (by plugins or game mechanics)
                val isForceLoaded = chunk.isForceLoaded

                // Calculate nearest player distance (in chunks)
                val nearestPlayerDist = if (playerChunks.isEmpty()) {
                    Int.MAX_VALUE
                } else {
                    playerChunks.minOf { (px, pz) ->
                        max(abs(chunkX - px), abs(chunkZ - pz))
                    }
                }

                chunksToProcess.add(ChunkData(
                    chunk = chunk,
                    x = chunkX,
                    z = chunkZ,
                    isSpawnChunk = isSpawn,
                    isForceLoaded = isForceLoaded,
                    nearestPlayerDistance = nearestPlayerDist
                ))
            }

            // Unload chunks that are:
            // 1. Not spawn chunks
            // 2. Not force loaded
            // 3. Not within view distance of any player
            for (chunkData in chunksToProcess) {
                if (chunkData.isSpawnChunk) continue
                if (chunkData.isForceLoaded) continue
                if (chunkData.nearestPlayerDistance <= viewDistance + 2) continue

                @Suppress("DEPRECATION")
                if (world.unloadChunkRequest(chunkData.x, chunkData.z)) {
                    totalUnloaded++
                }
            }
        }

        // Simple logging - only show when chunks were actually unloaded
        if (config.unloadChunksLog && totalUnloaded > 0) {
            plugin.logger.info("Unloaded $totalUnloaded chunks")
        }
    }

    /**
     * Check if a chunk is a spawn chunk using distance calculation
     * Paper/Spigot keeps chunks around spawn loaded based on keep-spawn-loaded-range
     */
    private fun isSpawnChunk(chunk: Chunk, world: World): Boolean {
        try {
            // First check if keep-spawn-in-memory is enabled
            val keepSpawn = try {
                val method = world.javaClass.getMethod("getKeepSpawnInMemory")
                method.invoke(world) as Boolean
            } catch (e: Exception) {
                true // Assume true if we can't check
            }

            if (!keepSpawn) return false

            // Get spawn location
            val spawnLoc = world.spawnLocation
            val spawnChunkX = spawnLoc.blockX shr 4
            val spawnChunkZ = spawnLoc.blockZ shr 4

            // Get actual spawn radius from Paper config
            val spawnRadius = getSpawnChunkRadius(world)

            val distX = abs(chunk.x - spawnChunkX)
            val distZ = abs(chunk.z - spawnChunkZ)

            return distX <= spawnRadius && distZ <= spawnRadius
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Get spawn chunk radius from Paper/Spigot config
     */
    private fun getSpawnChunkRadius(world: World): Int {
        // Try Paper's world-specific config first
        try {
            // Paper 1.19+ stores this in world config
            val worldClass = world.javaClass

            // Try to get spawnChunkRadius or keep-spawn-loaded-range
            // Paper stores this differently in different versions

            // Try Paper 1.20+ method
            try {
                val paperConfig = worldClass.getMethod("paperConfig").invoke(world)
                val chunksField = paperConfig.javaClass.getField("chunks")
                val chunks = chunksField.get(paperConfig)
                val keepSpawnField = chunks.javaClass.getField("keepSpawnLoadedRange")
                return keepSpawnField.getInt(chunks)
            } catch (e: Exception) {
                // Ignore and try next method
            }

            // Try older Paper method
            try {
                val spigotConfig = Bukkit.spigot().spigotConfig
                val worldSection = spigotConfig.getConfigurationSection("world-settings.${world.name}")
                    ?: spigotConfig.getConfigurationSection("world-settings.default")
                if (worldSection != null) {
                    return worldSection.getInt("keep-spawn-loaded-range", 10)
                }
            } catch (e: Exception) {
                // Ignore
            }

        } catch (e: Exception) {
            // Fallback
        }

        // Default spawn chunk radius for vanilla/Paper
        // Paper default: 10 chunks
        return 10
    }

    /**
     * Force unload all eligible chunks immediately
     */
    fun forceUnload(): Int {
        var total = 0

        for (world in Bukkit.getWorlds()) {
            if (!configuredWorlds.contains(world.name)) continue

            val viewDistance = Bukkit.getViewDistance()

            val playerChunks = world.players.map { player ->
                val chunk = player.location.chunk
                Pair(chunk.x, chunk.z)
            }

            for (chunk in world.loadedChunks) {
                // Skip spawn chunks
                if (isSpawnChunk(chunk, world)) {
                    continue
                }

                // Skip force loaded
                if (chunk.isForceLoaded) {
                    continue
                }

                // Skip near players
                val nearPlayer = playerChunks.any { (px, pz) ->
                    max(abs(chunk.x - px), abs(chunk.z - pz)) <= viewDistance + 2
                }
                if (nearPlayer) {
                    continue
                }

                @Suppress("DEPRECATION")
                if (world.unloadChunkRequest(chunk.x, chunk.z)) {
                    total++
                }
            }
        }

        return total
    }

    /**
     * Get detailed chunk statistics
     */
    fun getChunkStats(): Map<String, ChunkStats> {
        val stats = mutableMapOf<String, ChunkStats>()

        for (worldName in configuredWorlds) {
            val world = Bukkit.getWorld(worldName) ?: continue

            val loadedChunks = world.loadedChunks
            val viewDistance = Bukkit.getViewDistance()

            val playerChunks = world.players.map { player ->
                val chunk = player.location.chunk
                Pair(chunk.x, chunk.z)
            }

            var spawnChunks = 0
            var forceLoadedChunks = 0
            var playerNearbyChunks = 0
            var unloadableChunks = 0

            for (chunk in loadedChunks) {
                when {
                    isSpawnChunk(chunk, world) -> spawnChunks++
                    chunk.isForceLoaded -> forceLoadedChunks++
                    playerChunks.any { (px, pz) ->
                        max(abs(chunk.x - px), abs(chunk.z - pz)) <= viewDistance + 2
                    } -> playerNearbyChunks++
                    else -> unloadableChunks++
                }
            }

            stats[worldName] = ChunkStats(
                total = loadedChunks.size,
                spawnChunks = spawnChunks,
                forceLoaded = forceLoadedChunks,
                nearPlayers = playerNearbyChunks,
                unloadable = unloadableChunks,
                viewDistance = viewDistance
            )
        }

        return stats
    }

    fun getLoadedChunkCount(): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        for (worldName in configuredWorlds) {
            val world = Bukkit.getWorld(worldName) ?: continue
            counts[worldName] = world.loadedChunks.size
        }
        return counts
    }

    data class ChunkStats(
        val total: Int,
        val spawnChunks: Int,
        val forceLoaded: Int,
        val nearPlayers: Int,
        val unloadable: Int,
        val viewDistance: Int
    )
}
