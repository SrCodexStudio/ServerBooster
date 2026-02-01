package dev.srcodex.serverbooster.chunk

import dev.srcodex.serverbooster.ServerBoosterPlugin
import dev.srcodex.serverbooster.util.SchedulerUtil
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPhysicsEvent
import java.util.concurrent.ConcurrentHashMap

class BlockPhysicsDetector(private val plugin: ServerBoosterPlugin) : Listener {

    private val config get() = plugin.configManager.chunkOptimizerConfig
    private val chunkPhysics = ConcurrentHashMap<Long, ChunkPhysicsData>()
    private var checkTask: Any? = null

    private val configuredWorlds: Set<String> = config.blockPhysicsWorlds.toSet()

    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
        startChecking()
    }

    fun unregister() {
        HandlerList.unregisterAll(this)
        SchedulerUtil.cancelTask(checkTask)
        chunkPhysics.clear()
    }

    private fun startChecking() {
        // Check every 15 seconds (300 ticks)
        checkTask = SchedulerUtil.runAsyncTimer(20L, 300L) {
            checkForLagMachines()
        }
    }

    @EventHandler
    fun onBlockPhysics(event: BlockPhysicsEvent) {
        val tps = plugin.getTps()
        if (tps > config.blockPhysicsLowTps) return

        val world = event.block.world
        if (!configuredWorlds.contains(world.name)) return

        val chunk = event.block.chunk
        val chunkKey = getChunkKey(chunk)

        val data = chunkPhysics.computeIfAbsent(chunkKey) {
            ChunkPhysicsData(chunk)
        }
        data.incrementCount()

        // Cancel if too many updates and configured
        if (config.blockPhysicsCancelEvent && data.count > config.blockPhysicsWarningThreshold) {
            event.isCancelled = true
        }
    }

    private fun checkForLagMachines() {
        val tps = plugin.getTps()
        if (tps > config.blockPhysicsLowTps) return

        val warnings = mutableListOf<Pair<String, ChunkPhysicsData>>()

        // Collect warnings (async safe)
        for ((_, data) in chunkPhysics) {
            if (data.count <= config.blockPhysicsWarningThreshold) continue
            val message = plugin.languageManager.getLimiterMessage(
                "chunk-too-many-updates",
                "count" to data.count,
                "coords" to data.coordsString
            )
            warnings.add(message to data)
        }

        if (warnings.isEmpty()) return

        // Send notifications on main thread
        SchedulerUtil.runTask {
            for ((message, data) in warnings) {
                // Notify OPs
                if (config.blockPhysicsNotifyOp) {
                    for (player in Bukkit.getOnlinePlayers()) {
                        if (player.isOp) {
                            player.sendMessage(message)
                        }
                    }
                }

                // Log warning
                plugin.logger.warning(message)

                // Reset count
                data.resetCount()
            }
        }
    }

    fun getReport(): String {
        val sb = StringBuilder()

        if (plugin.getTps() <= config.blockPhysicsLowTps) {
            sb.append("§cLOW TPS: §f${String.format("%.2f", plugin.getTps())}\n\n")
        }

        val sorted = chunkPhysics.entries
            .sortedByDescending { it.value.count }
            .take(30)

        for ((_, data) in sorted) {
            sb.append("${data.worldName}: ${data.coordsString}: ${data.count} max per minute updates\n")
        }

        if (chunkPhysics.size > 30) {
            sb.append("... skipped ${chunkPhysics.size - 30}, too many values\n")
        }

        return sb.toString()
    }

    private fun getChunkKey(chunk: Chunk): Long {
        return chunk.x.toLong() shl 32 or (chunk.z.toLong() and 0xFFFFFFFFL)
    }

    private data class ChunkPhysicsData(
        val chunk: Chunk
    ) {
        var count: Int = 0
            private set

        val worldName: String = chunk.world.name
        val coordsString: String = "${chunk.x * 16}, ${chunk.z * 16}"

        fun incrementCount() {
            count++
        }

        fun resetCount() {
            count = 0
        }
    }
}
