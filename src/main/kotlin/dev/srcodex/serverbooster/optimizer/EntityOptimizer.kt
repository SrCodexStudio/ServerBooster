package dev.srcodex.serverbooster.optimizer

import dev.srcodex.serverbooster.ServerBoosterPlugin
import dev.srcodex.serverbooster.util.MinecraftVersion
import dev.srcodex.serverbooster.util.SchedulerUtil
import kotlinx.coroutines.*
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.event.world.WorldLoadEvent
import org.bukkit.event.world.WorldUnloadEvent
import java.util.concurrent.ConcurrentHashMap

class EntityOptimizer(private val plugin: ServerBoosterPlugin) : Listener {

    private val config get() = plugin.configManager.entityOptimizerConfig

    // Cache of entities that have been untracked
    private val untrackedEntities = ConcurrentHashMap<String, MutableSet<Int>>()
    private val frozenEntities = ConcurrentHashMap<String, MutableSet<Int>>()

    // Scheduler tasks
    private var alwaysTask: Any? = null
    private var tpsTask: Any? = null
    private var restoreTask: Any? = null
    private var tpsLogTask: Any? = null

    // Optimization state
    @Volatile
    private var isOptimizing = false

    // Coroutine scope for batch processing
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Batch size for entity processing (prevents lag spikes)
    private val entityBatchSize = 100

    fun start() {
        if (!MinecraftVersion.isAtLeast(1, 14)) {
            plugin.logger.warning("Entity Optimizer requires Minecraft 1.14+")
            return
        }

        Bukkit.getPluginManager().registerEvents(this, plugin)

        // Initialize world caches
        for (world in Bukkit.getWorlds()) {
            if (config.worlds.contains(world.name)) {
                initializeWorldCache(world.name)
            }
        }

        // Start always trigger
        if (config.triggerAlways.enabled) {
            alwaysTask = SchedulerUtil.runTaskTimer(
                config.triggerAlways.untrackTicks.toLong(),
                config.triggerAlways.untrackTicks.toLong()
            ) {
                runOptimization(OptimizationTrigger.ALWAYS)
            }
        }

        // Start TPS-based trigger
        if (config.triggerWhenTpsBelow.enabled) {
            tpsTask = SchedulerUtil.runTaskTimer(
                config.triggerWhenTpsBelow.untrackTicks.toLong(),
                config.triggerWhenTpsBelow.untrackTicks.toLong()
            ) {
                val tps = plugin.getTps()
                if (tps < config.triggerWhenTpsBelow.value) {
                    runOptimization(OptimizationTrigger.LOW_TPS)
                }
            }
        }

        // Start restore task with coroutine-based batch processing
        restoreTask = SchedulerUtil.runTaskTimer(
            config.checkUntrackedEntitiesFrequency.toLong() + 1,
            config.checkUntrackedEntitiesFrequency.toLong()
        ) {
            scope.launch {
                restoreNearbyEntitiesBatched()
            }
        }

        // Start TPS logging
        if (config.logTps.enabled) {
            tpsLogTask = SchedulerUtil.runAsyncTimer(1L, config.logTps.interval.toLong()) {
                plugin.logger.info("TPS: ${String.format("%.2f", plugin.getTps())}")
            }
        }

        // Fix AI from previous update if enabled
        if (config.fixAiPreviousUpdate) {
            for (world in Bukkit.getWorlds()) {
                restoreWorldEntities(world)
            }
        }

        plugin.logger.info("Entity Optimizer started with tracking range: ${config.trackingRange}")
    }

    fun shutdown() {
        HandlerList.unregisterAll(this)

        SchedulerUtil.cancelTask(alwaysTask)
        SchedulerUtil.cancelTask(tpsTask)
        SchedulerUtil.cancelTask(restoreTask)
        SchedulerUtil.cancelTask(tpsLogTask)

        // Cancel coroutines
        scope.cancel()

        // Restore all entities
        for (world in Bukkit.getWorlds()) {
            restoreWorldEntities(world)
        }

        untrackedEntities.clear()
        frozenEntities.clear()
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onWorldLoad(event: WorldLoadEvent) {
        if (config.worlds.contains(event.world.name)) {
            initializeWorldCache(event.world.name)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onWorldUnload(event: WorldUnloadEvent) {
        // Restore entities before world unloads
        restoreWorldEntities(event.world)

        untrackedEntities.remove(event.world.name)
        frozenEntities.remove(event.world.name)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onChunkUnload(event: ChunkUnloadEvent) {
        if (!config.worlds.contains(event.world.name)) return

        // Restore entities in chunk before unload
        for (entity in event.chunk.entities) {
            restoreEntity(entity)
        }
    }

    private fun initializeWorldCache(worldName: String) {
        untrackedEntities[worldName] = ConcurrentHashMap.newKeySet()
        frozenEntities[worldName] = ConcurrentHashMap.newKeySet()
    }

    private fun runOptimization(trigger: OptimizationTrigger) {
        if (isOptimizing) return
        isOptimizing = true

        // Use coroutine for coordinated batch processing
        scope.launch {
            try {
                var totalOptimized = 0

                for (worldName in config.worlds) {
                    val optimized = optimizeWorldBatched(worldName)
                    totalOptimized += optimized
                }

                if (config.logToConsole && totalOptimized > 0) {
                    plugin.logger.info("[$trigger] Optimized $totalOptimized entities")
                }
            } catch (e: Exception) {
                plugin.logger.warning("Error during optimization: ${e.message}")
            } finally {
                isOptimizing = false
            }
        }
    }

    /**
     * Optimizes entities in batches to prevent lag spikes
     */
    private suspend fun optimizeWorldBatched(worldName: String): Int {
        var optimized = 0
        val trackingRangeSquared = config.trackingRange * config.trackingRange

        // Collect entity data on main thread
        val entitiesToProcess = mutableListOf<Entity>()

        runOnMainThread {
            val world = Bukkit.getWorld(worldName) ?: return@runOnMainThread
            val playerLocations = world.players.map { it.location }

            for (entity in world.livingEntities) {
                if (shouldProcessEntity(entity) && !isNearAnyPlayer(entity, playerLocations, trackingRangeSquared)) {
                    entitiesToProcess.add(entity)
                }
            }
        }

        // Process in batches on main thread
        for (batch in entitiesToProcess.chunked(entityBatchSize)) {
            runOnMainThread {
                for (entity in batch) {
                    if (!entity.isDead && optimizeEntity(entity)) {
                        optimized++
                    }
                }
            }

            // Small delay between batches to let server breathe
            if (batch.size == entityBatchSize) {
                delay(1)
            }
        }

        return optimized
    }

    /**
     * Helper to run code on main thread and suspend until complete
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun runOnMainThread(block: () -> Unit) {
        if (Bukkit.isPrimaryThread()) {
            block()
            return
        }

        suspendCancellableCoroutine { continuation ->
            SchedulerUtil.runTask {
                try {
                    block()
                    continuation.resume(Unit) { }
                } catch (e: Exception) {
                    continuation.cancel(e)
                }
            }
        }
    }

    private fun optimizeWorld(world: World): Int {
        var optimized = 0
        val trackingRangeSquared = config.trackingRange * config.trackingRange

        // Get online player locations
        val playerLocations = world.players.map { it.location }

        // Process entities - must be on main thread
        val entitiesToProcess = world.livingEntities.filter { entity ->
            shouldProcessEntity(entity) && !isNearAnyPlayer(entity, playerLocations, trackingRangeSquared)
        }

        for (entity in entitiesToProcess) {
            if (optimizeEntity(entity)) {
                optimized++
            }
        }

        return optimized
    }

    private fun shouldProcessEntity(entity: Entity): Boolean {
        // Skip certain entity types
        if (entity is Player) return false
        if (entity is EnderDragon) return false
        if (entity is Arrow || entity is SpectralArrow) return false

        // Check ignore options
        val ignore = config.ignore

        if (ignore.drops && entity is Item) return false
        if (ignore.itemFrames && entity is ItemFrame) return false
        if (ignore.armorStands && entity is ArmorStand) return false
        if (ignore.villagers && entity is Villager) return false
        if (ignore.customNamed && entity.customName != null) return false
        if (ignore.invulnerable && entity.isInvulnerable) return false

        return true
    }

    private fun isNearAnyPlayer(entity: Entity, playerLocations: List<org.bukkit.Location>, rangeSquared: Int): Boolean {
        val entityLoc = entity.location
        return playerLocations.any { playerLoc ->
            if (playerLoc.world != entityLoc.world) return@any false
            playerLoc.distanceSquared(entityLoc) <= rangeSquared
        }
    }

    private fun optimizeEntity(entity: Entity): Boolean {
        val worldCache = frozenEntities[entity.world.name] ?: return false

        if (worldCache.contains(entity.entityId)) return false

        // Disable ticking
        if (config.disableTickForUntrackedEntities) {
            plugin.nmsManager.setEntityTicking(entity, false)
        }

        // Disable AI
        if (config.disableAiForUntrackedEntities && entity is LivingEntity) {
            plugin.nmsManager.setEntityAI(entity, false)
            plugin.nmsManager.setFrozenTag(entity)
        }

        worldCache.add(entity.entityId)

        if (config.logDetailed) {
            plugin.logger.info("Optimized: ${entity.type} at ${entity.location}")
        }

        return true
    }

    /**
     * Restore entities near players using batch processing
     */
    private suspend fun restoreNearbyEntitiesBatched() {
        val trackingRangeSquared = config.trackingRange * config.trackingRange

        for (worldName in config.worlds) {
            val entitiesToRestore = mutableListOf<Entity>()

            // Collect entities to restore on main thread
            runOnMainThread {
                val world = Bukkit.getWorld(worldName) ?: return@runOnMainThread
                val worldCache = frozenEntities[worldName] ?: return@runOnMainThread
                val playerLocations = world.players.map { it.location }

                if (playerLocations.isEmpty()) return@runOnMainThread

                for (entity in world.livingEntities) {
                    val inCache = worldCache.contains(entity.entityId)
                    val hasFrozenTag = plugin.nmsManager.hasFrozenTag(entity)

                    if (hasFrozenTag && !inCache) {
                        worldCache.add(entity.entityId)
                    }

                    if (!inCache && !hasFrozenTag) continue

                    if (isNearAnyPlayer(entity, playerLocations, trackingRangeSquared)) {
                        entitiesToRestore.add(entity)
                    }
                }
            }

            // Restore in batches
            for (batch in entitiesToRestore.chunked(entityBatchSize)) {
                runOnMainThread {
                    for (entity in batch) {
                        if (!entity.isDead) {
                            restoreEntity(entity)
                        }
                    }
                }

                if (batch.size == entityBatchSize) {
                    delay(1)
                }
            }
        }
    }

    private fun restoreNearbyEntities() {
        val trackingRangeSquared = config.trackingRange * config.trackingRange

        for (worldName in config.worlds) {
            val world = Bukkit.getWorld(worldName) ?: continue
            val worldCache = frozenEntities[worldName] ?: continue
            val playerLocations = world.players.map { it.location }

            // Skip if no players
            if (playerLocations.isEmpty()) continue

            for (entity in world.livingEntities) {
                // Check BOTH cache AND PDC tag (PDC tag persists across reconnects)
                val inCache = worldCache.contains(entity.entityId)
                val hasFrozenTag = plugin.nmsManager.hasFrozenTag(entity)

                // If entity has frozen tag but not in cache, add it to cache
                if (hasFrozenTag && !inCache) {
                    worldCache.add(entity.entityId)
                }

                // Skip if not frozen
                if (!inCache && !hasFrozenTag) continue

                // Restore if near any player
                if (isNearAnyPlayer(entity, playerLocations, trackingRangeSquared)) {
                    restoreEntity(entity)
                }
            }
        }
    }

    private fun restoreEntity(entity: Entity) {
        val worldCache = frozenEntities[entity.world.name]
        val hasFrozenTag = plugin.nmsManager.hasFrozenTag(entity)
        val inCache = worldCache?.contains(entity.entityId) == true

        // Skip if not frozen at all
        if (!hasFrozenTag && !inCache) return

        // Restore ticking
        if (config.disableTickForUntrackedEntities) {
            plugin.nmsManager.setEntityTicking(entity, true)
        }

        // Restore AI - ALWAYS restore if entity has frozen tag
        if (entity is LivingEntity) {
            if (hasFrozenTag || config.disableAiForUntrackedEntities) {
                plugin.nmsManager.setEntityAI(entity, true)
                plugin.nmsManager.removeFrozenTag(entity)
            }
        }

        // Remove from cache
        worldCache?.remove(entity.entityId)

        if (config.logDetailed) {
            plugin.logger.info("Restored: ${entity.type} at ${entity.location}")
        }
    }

    private fun restoreWorldEntities(world: World) {
        if (!config.worlds.contains(world.name)) return

        for (entity in world.livingEntities) {
            if (plugin.nmsManager.hasFrozenTag(entity)) {
                plugin.nmsManager.setEntityAI(entity, true)
                plugin.nmsManager.removeFrozenTag(entity)
            }
        }

        frozenEntities[world.name]?.clear()
    }

    fun forceOptimize(): Int {
        var total = 0

        for (worldName in config.worlds) {
            val world = Bukkit.getWorld(worldName) ?: continue
            total += optimizeWorld(world)
        }

        return total
    }

    fun getOptimizedCount(): Int {
        return frozenEntities.values.sumOf { it.size }
    }

    enum class OptimizationTrigger {
        ALWAYS,
        LOW_TPS,
        MANUAL
    }
}
