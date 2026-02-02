package dev.srcodex.serverbooster.chunk

import dev.srcodex.serverbooster.ServerBoosterPlugin
import dev.srcodex.serverbooster.util.SchedulerUtil
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.World
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max

/**
 * ChunkOptimizer - Sistema de descarga de chunks inactivos para Paper
 *
 * ENFOQUE SIMPLIFICADO (basado en NebulaBooster que SI funciona):
 * ===============================================================
 * 1. Trackear cuando se cargan los chunks
 * 2. Verificaciones MINIMAS antes de intentar unload:
 *    - No es spawn chunk
 *    - No esta force-loaded
 *    - Esta lejos de jugadores
 *    - Ha estado inactivo suficiente tiempo
 * 3. Llamar chunk.unload(true) y DEJAR QUE PAPER DECIDA
 * 4. Si Paper rechaza, continuar sin complicaciones
 *
 * La clave es NO pre-filtrar por loadLevel u otros criterios internos.
 * Paper sabe mejor que nosotros cuando puede descargar un chunk.
 */
class ChunkOptimizer(private val plugin: ServerBoosterPlugin) : Listener {

    // ============================================================================
    // CONFIGURACION
    // ============================================================================

    private val config get() = plugin.configManager.chunkOptimizerConfig

    /** Mundos donde el optimizer esta activo */
    private val enabledWorlds: Set<String> get() = config.unloadChunksWorlds.toSet()

    /** Intervalo entre ciclos de mantenimiento (en ticks) */
    private val maintenanceIntervalTicks: Long get() = config.unloadChunksInterval.toLong()

    /** Tiempo que un chunk debe estar inactivo antes de poder ser descargado (ms) */
    private val inactivityThresholdMs: Long get() = config.unloadChunksInactiveTimeout * 1000L

    /** Distancia minima en chunks desde cualquier jugador (usa el mayor entre config y view-distance) */
    private val minPlayerDistance: Int
        get() = max(config.unloadChunksMinDistance, Bukkit.getViewDistance())

    /** Radio de proteccion alrededor del spawn */
    private val spawnProtectionRadius: Int get() = config.unloadChunksSpawnRadius

    /** Log cuando se descargan chunks */
    private val logUnloads: Boolean get() = config.unloadChunksLog

    /** Log detallado para debugging */
    private val debugMode: Boolean get() = config.unloadChunksDebug

    // ============================================================================
    // TRACKING DE CHUNKS (Simplificado)
    // ============================================================================

    /**
     * Mapa de timestamps de ultima actividad por chunk.
     * Key: ChunkKey (combinacion de world hash, x, z)
     * Value: Timestamp de ultima actividad/carga
     */
    private val chunkLastActivity = ConcurrentHashMap<Long, AtomicLong>()

    // ============================================================================
    // ESTADO INTERNO
    // ============================================================================

    /** Tarea de mantenimiento principal */
    private var maintenanceTask: Any? = null

    /** Tarea de limpieza de datos obsoletos */
    private var cleanupTask: Any? = null

    /** Flag para evitar ejecuciones concurrentes del ciclo de mantenimiento */
    private val isProcessing = AtomicBoolean(false)

    // ============================================================================
    // ESTADISTICAS
    // ============================================================================

    private val totalCyclesRun = AtomicInteger(0)
    private val totalChunksUnloaded = AtomicLong(0)

    // Peak tracking
    private var peakChunksLoaded = 0
    private var peakChunksTime = 0L

    // ============================================================================
    // INICIALIZACION Y SHUTDOWN
    // ============================================================================

    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
        startTasks()
    }

    /**
     * Detiene el optimizer y limpia recursos.
     */
    fun shutdown() {
        // Desregistrar listeners
        ChunkLoadEvent.getHandlerList().unregister(this)
        ChunkUnloadEvent.getHandlerList().unregister(this)

        // Cancelar tareas
        SchedulerUtil.cancelTask(maintenanceTask)
        SchedulerUtil.cancelTask(cleanupTask)
        maintenanceTask = null
        cleanupTask = null

        // Limpiar datos
        chunkLastActivity.clear()
        isProcessing.set(false)

        plugin.logger.info("[ChunkOptimizer] Shutdown complete. Total cycles: ${totalCyclesRun.get()}, " +
                "chunks unloaded: ${totalChunksUnloaded.get()}")
    }

    /**
     * Inicia las tareas periodicas del optimizer.
     */
    private fun startTasks() {
        if (!config.unloadChunksEnabled) {
            plugin.logger.info("[ChunkOptimizer] Disabled in configuration")
            return
        }

        plugin.logger.info("[ChunkOptimizer] Starting with SIMPLIFIED approach:")
        plugin.logger.info("  - Interval: ${maintenanceIntervalTicks} ticks (${maintenanceIntervalTicks / 20.0}s)")
        plugin.logger.info("  - Inactivity timeout: ${inactivityThresholdMs / 1000}s")
        plugin.logger.info("  - Min player distance: $minPlayerDistance chunks")
        plugin.logger.info("  - Spawn protection: $spawnProtectionRadius chunks")
        plugin.logger.info("  - Worlds: $enabledWorlds")
        plugin.logger.info("  - Debug mode: $debugMode")

        // Tarea principal de mantenimiento
        // Delay inicial de 5 segundos para que el servidor cargue completamente
        maintenanceTask = SchedulerUtil.runTaskTimer(100L, maintenanceIntervalTicks) {
            runMaintenanceCycle()
        }

        // Tarea de limpieza de datos obsoletos cada 5 minutos
        cleanupTask = SchedulerUtil.runAsyncTimer(6000L, 6000L) {
            cleanupStaleData()
        }

        plugin.logger.info("[ChunkOptimizer] Started successfully")
    }

    // ============================================================================
    // EVENT HANDLERS
    // ============================================================================

    /**
     * Registra cuando un chunk es cargado.
     * Esto inicia el contador de inactividad para ese chunk.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onChunkLoad(event: ChunkLoadEvent) {
        if (!config.unloadChunksEnabled) return
        if (!enabledWorlds.contains(event.world.name)) return

        val chunk = event.chunk
        val key = createChunkKey(chunk.x, chunk.z, event.world.name)
        val now = System.currentTimeMillis()

        chunkLastActivity.compute(key) { _, existing ->
            existing?.apply { set(now) } ?: AtomicLong(now)
        }

        // Track peak
        val totalLoaded = getTotalLoadedChunks()
        if (totalLoaded > peakChunksLoaded) {
            peakChunksLoaded = totalLoaded
            peakChunksTime = now
        }

        if (debugMode) {
            plugin.logger.info("[ChunkOptimizer] Chunk loaded: ${chunk.x},${chunk.z} in ${event.world.name}")
        }
    }

    /**
     * Registra cuando un chunk es descargado para limpiar datos.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onChunkUnload(event: ChunkUnloadEvent) {
        if (!config.unloadChunksEnabled) return
        if (!enabledWorlds.contains(event.world.name)) return

        val chunk = event.chunk
        val key = createChunkKey(chunk.x, chunk.z, event.world.name)

        // Limpiar datos de tracking
        chunkLastActivity.remove(key)

        if (debugMode) {
            plugin.logger.info("[ChunkOptimizer] Chunk unloaded: ${chunk.x},${chunk.z} in ${event.world.name}")
        }
    }

    // ============================================================================
    // CICLO DE MANTENIMIENTO (ENFOQUE SIMPLIFICADO - ESTILO NEBULABOOSTER)
    // ============================================================================

    /**
     * Ejecuta un ciclo completo de mantenimiento.
     * Enfoque simplificado: verificaciones minimas, dejar que Paper decida.
     */
    private fun runMaintenanceCycle() {
        // Verificar si hay un ciclo anterior corriendo
        if (!isProcessing.compareAndSet(false, true)) {
            return
        }

        val cycleNumber = totalCyclesRun.incrementAndGet()
        val now = System.currentTimeMillis()

        if (debugMode) {
            plugin.logger.info("[ChunkOptimizer] === Starting maintenance cycle #$cycleNumber ===")
        }

        try {
            var totalUnloaded = 0

            for (worldName in enabledWorlds) {
                val world = Bukkit.getWorld(worldName) ?: continue
                totalUnloaded += processWorldChunks(world, now)
            }

            // Actualizar estadisticas
            totalChunksUnloaded.addAndGet(totalUnloaded.toLong())

            // Log resultados (solo si hay exitos, igual que NebulaBooster)
            if (totalUnloaded > 0 && logUnloads) {
                plugin.logger.info("[ChunkOptimizer] Cycle #$cycleNumber: Unloaded $totalUnloaded chunks")
            }

            if (debugMode) {
                plugin.logger.info("[ChunkOptimizer] Cycle #$cycleNumber complete: unloaded=$totalUnloaded")
            }

        } catch (e: Exception) {
            plugin.logger.warning("[ChunkOptimizer] Error in maintenance cycle: ${e.message}")
            if (debugMode) {
                e.printStackTrace()
            }
        } finally {
            isProcessing.set(false)
        }
    }

    /**
     * Procesa los chunks de un mundo (enfoque NebulaBooster).
     * Verificaciones MINIMAS, dejar que Paper decida con chunk.unload(true).
     *
     * @return Numero de chunks descargados
     */
    private fun processWorldChunks(world: World, now: Long): Int {
        var unloaded = 0

        // Pre-calcular posiciones de jugadores
        val playerChunkPositions = world.players.map { player ->
            Pair(player.location.blockX shr 4, player.location.blockZ shr 4)
        }

        // Iterar chunks cargados
        for (chunk in world.loadedChunks) {
            // Verificacion 1: No es spawn chunk
            if (isSpawnChunk(chunk, world)) continue

            // Verificacion 2: No esta force-loaded
            if (chunk.isForceLoaded) continue

            // Verificacion 3: Esta lejos de todos los jugadores
            if (!isChunkFarFromPlayers(chunk, playerChunkPositions)) continue

            // Verificacion 4: Ha estado inactivo el tiempo suficiente
            if (!isChunkInactive(chunk, world.name, now)) continue

            // INTENTAR DESCARGAR usando unloadChunkRequest (método del plugin original)
            // Este método es deprecated pero es más "suave" - solo hace una solicitud
            try {
                @Suppress("DEPRECATION")
                if (world.unloadChunkRequest(chunk.x, chunk.z)) {
                    val key = createChunkKey(chunk.x, chunk.z, world.name)
                    chunkLastActivity.remove(key)
                    unloaded++

                    if (debugMode) {
                        plugin.logger.info("[ChunkOptimizer] Unload requested for chunk ${chunk.x},${chunk.z} in ${world.name}")
                    }
                }
                // Si el servidor rechaza, continuamos silenciosamente
            } catch (e: Exception) {
                // Continuar con otros chunks
            }
        }

        if (unloaded > 0 && logUnloads) {
            plugin.logger.info("[ChunkOptimizer] Unloaded $unloaded inactive chunks in ${world.name}")
        }

        return unloaded
    }

    // ============================================================================
    // VERIFICACIONES DE CHUNKS (SIMPLIFICADAS - ESTILO NEBULABOOSTER)
    // ============================================================================

    /**
     * Obtiene el load level de un chunk de forma segura.
     * Solo se usa para estadisticas, NO para filtrar candidatos.
     */
    private fun getLoadLevelSafe(chunk: Chunk): String {
        return try {
            chunk.loadLevel.name
        } catch (e: Exception) {
            "UNKNOWN"
        }
    }

    /**
     * Verifica si un chunk esta dentro del area de spawn.
     * Similar a NebulaBooster: radio de 2 chunks por defecto.
     */
    private fun isSpawnChunk(chunk: Chunk, world: World): Boolean {
        val keepSpawn = try {
            world.keepSpawnInMemory
        } catch (e: Exception) {
            true
        }

        if (!keepSpawn) return false

        val spawn = world.spawnLocation
        val spawnChunkX = spawn.blockX shr 4
        val spawnChunkZ = spawn.blockZ shr 4

        return abs(chunk.x - spawnChunkX) <= spawnProtectionRadius &&
               abs(chunk.z - spawnChunkZ) <= spawnProtectionRadius
    }

    /**
     * Verifica si un chunk esta lo suficientemente lejos de todos los jugadores.
     * Usa Pair<Int, Int> para mayor simplicidad (estilo NebulaBooster).
     */
    private fun isChunkFarFromPlayers(chunk: Chunk, playerPositions: List<Pair<Int, Int>>): Boolean {
        if (playerPositions.isEmpty()) return true

        return playerPositions.none { (playerX, playerZ) ->
            val distance = chebyshevDistance(chunk.x, chunk.z, playerX, playerZ)
            distance <= minPlayerDistance
        }
    }

    /**
     * Verifica si un chunk ha estado inactivo el tiempo suficiente.
     */
    private fun isChunkInactive(chunk: Chunk, worldName: String, now: Long): Boolean {
        val key = createChunkKey(chunk.x, chunk.z, worldName)
        val lastActivity = chunkLastActivity[key]

        if (lastActivity == null) {
            // Chunk nuevo, registrar y no considerarlo inactivo aun
            chunkLastActivity[key] = AtomicLong(now)
            return false
        }

        val inactiveTime = now - lastActivity.get()
        return inactiveTime >= inactivityThresholdMs
    }

    /**
     * Calcula la distancia Chebyshev entre dos posiciones de chunks.
     */
    private fun chebyshevDistance(x1: Int, z1: Int, x2: Int, z2: Int): Int {
        return max(abs(x1 - x2), abs(z1 - z2))
    }

    // ============================================================================
    // LIMPIEZA DE DATOS
    // ============================================================================

    /**
     * Limpia datos de tracking para chunks que ya no estan cargados.
     */
    private fun cleanupStaleData() {
        val now = System.currentTimeMillis()
        val tenMinutesAgo = now - 600_000L
        val removedActivity = AtomicInteger(0)

        // Limpiar activity tracking muy antiguo
        chunkLastActivity.entries.removeIf { (_, lastActivity) ->
            val shouldRemove = lastActivity.get() < tenMinutesAgo
            if (shouldRemove) removedActivity.incrementAndGet()
            shouldRemove
        }

        if (debugMode && removedActivity.get() > 0) {
            plugin.logger.info("[ChunkOptimizer] Cleanup: removed ${removedActivity.get()} stale entries. Remaining: ${chunkLastActivity.size}")
        }
    }

    // ============================================================================
    // UTILIDADES
    // ============================================================================

    /**
     * Crea una key unica para identificar un chunk.
     */
    private fun createChunkKey(x: Int, z: Int, worldName: String): Long {
        val worldHash = worldName.hashCode().toLong() and 0xFFFF
        val xBits = x.toLong() and 0xFFFFFF
        val zBits = z.toLong() and 0xFFFFFF
        return (worldHash shl 48) or (xBits shl 24) or zBits
    }

    /**
     * Obtiene el total de chunks cargados en mundos monitoreados.
     */
    private fun getTotalLoadedChunks(): Int {
        return enabledWorlds.sumOf { worldName ->
            Bukkit.getWorld(worldName)?.loadedChunks?.size ?: 0
        }
    }

    /**
     * Extrae el nombre del mundo de una chunk key.
     */
    private fun getWorldNameFromKey(key: Long): String {
        val worldHash = ((key shr 48) and 0xFFFF).toInt()
        for (worldName in enabledWorlds) {
            if ((worldName.hashCode() and 0xFFFF) == worldHash) {
                return worldName
            }
        }
        return "unknown"
    }

    // ============================================================================
    // API PUBLICA
    // ============================================================================

    /**
     * Registra actividad en un chunk, reiniciando su contador de inactividad.
     */
    fun recordChunkActivity(chunk: Chunk) {
        if (!config.unloadChunksEnabled) return
        if (!enabledWorlds.contains(chunk.world.name)) return

        val key = createChunkKey(chunk.x, chunk.z, chunk.world.name)
        val now = System.currentTimeMillis()

        chunkLastActivity.compute(key) { _, existing ->
            existing?.apply { set(now) } ?: AtomicLong(now)
        }
    }

    /**
     * Fuerza un ciclo de descarga inmediato (enfoque NebulaBooster).
     * Solo protecciones basicas, dejar que Paper decida.
     *
     * @return Numero de chunks descargados
     */
    fun forceUnloadCycle(): Int {
        if (!config.unloadChunksEnabled) {
            plugin.logger.info("[ChunkOptimizer] Cannot force unload - optimizer is disabled")
            return 0
        }

        plugin.logger.info("[ChunkOptimizer] Force unload cycle requested")

        var totalUnloaded = 0

        for (worldName in enabledWorlds) {
            val world = Bukkit.getWorld(worldName) ?: continue

            val playerPositions = world.players.map { player ->
                Pair(player.location.blockX shr 4, player.location.blockZ shr 4)
            }

            for (chunk in world.loadedChunks) {
                // Protecciones MINIMAS (estilo NebulaBooster)
                if (isSpawnChunk(chunk, world)) continue
                if (chunk.isForceLoaded) continue
                if (!isChunkFarFromPlayers(chunk, playerPositions)) continue

                // INTENTAR DESCARGAR usando unloadChunkRequest (método del plugin original)
                try {
                    @Suppress("DEPRECATION")
                    if (world.unloadChunkRequest(chunk.x, chunk.z)) {
                        val key = createChunkKey(chunk.x, chunk.z, worldName)
                        chunkLastActivity.remove(key)
                        totalUnloaded++
                    }
                    // Si el servidor rechaza, simplemente continuamos
                } catch (e: Exception) {
                    // Ignorar errores, continuar
                }
            }
        }

        totalChunksUnloaded.addAndGet(totalUnloaded.toLong())
        if (totalUnloaded > 0) {
            plugin.logger.info("[ChunkOptimizer] Force unload: $totalUnloaded chunks unloaded")
        } else {
            plugin.logger.info("[ChunkOptimizer] Force unload: Paper is managing chunks optimally")
        }
        return totalUnloaded
    }

    /**
     * Obtiene estadisticas detalladas del estado actual.
     */
    fun getStatistics(): Map<String, Any> {
        val stats = mutableMapOf<String, Any>()

        stats["enabled"] = config.unloadChunksEnabled
        stats["totalCycles"] = totalCyclesRun.get()
        stats["totalUnloaded"] = totalChunksUnloaded.get()
        stats["trackedChunks"] = chunkLastActivity.size
        stats["isProcessing"] = isProcessing.get()
        stats["peakChunksLoaded"] = peakChunksLoaded
        stats["peakChunksTime"] = peakChunksTime

        // Stats por mundo
        val worldStats = mutableMapOf<String, Map<String, Int>>()
        for (worldName in enabledWorlds) {
            val world = Bukkit.getWorld(worldName) ?: continue
            worldStats[worldName] = mapOf(
                "loaded" to world.loadedChunks.size,
                "tracked" to chunkLastActivity.count {
                    getWorldNameFromKey(it.key) == worldName
                }
            )
        }
        stats["worlds"] = worldStats

        return stats
    }

    /**
     * Obtiene estadisticas detalladas de chunks por mundo.
     */
    fun getChunkStats(): Map<String, ChunkStats> {
        val result = mutableMapOf<String, ChunkStats>()
        val now = System.currentTimeMillis()

        for (worldName in enabledWorlds) {
            val world = Bukkit.getWorld(worldName) ?: continue
            val loadedChunks = world.loadedChunks

            val playerPositions = world.players.map { player ->
                Pair(player.location.blockX shr 4, player.location.blockZ shr 4)
            }

            var spawnChunks = 0
            var forceLoaded = 0
            var nearPlayers = 0
            var inactive = 0
            var active = 0
            var withTickets = 0

            // Load level counts
            var entityTicking = 0
            var ticking = 0
            var border = 0

            for (chunk in loadedChunks) {
                // Count by load level
                when (getLoadLevelSafe(chunk)) {
                    "ENTITY_TICKING" -> entityTicking++
                    "TICKING" -> ticking++
                    "BORDER" -> border++
                }

                // Contar tickets
                if (chunk.pluginChunkTickets.isNotEmpty()) {
                    withTickets++
                }

                when {
                    isSpawnChunk(chunk, world) -> spawnChunks++
                    chunk.isForceLoaded -> forceLoaded++
                    !isChunkFarFromPlayers(chunk, playerPositions) -> nearPlayers++
                    isChunkInactive(chunk, worldName, now) -> inactive++
                    else -> active++
                }
            }

            result[worldName] = ChunkStats(
                total = loadedChunks.size,
                spawnChunks = spawnChunks,
                forceLoaded = forceLoaded,
                nearPlayers = nearPlayers,
                inactive = inactive,
                active = active,
                withPluginTickets = withTickets,
                entityTicking = entityTicking,
                ticking = ticking,
                border = border
            )
        }

        return result
    }

    /**
     * Obtiene conteo de chunks cargados por mundo.
     */
    fun getLoadedChunkCounts(): Map<String, Int> {
        return enabledWorlds.mapNotNull { worldName ->
            Bukkit.getWorld(worldName)?.let { world ->
                worldName to world.loadedChunks.size
            }
        }.toMap()
    }

    /**
     * Genera un reporte de diagnostico para el administrador.
     */
    fun getDiagnosticReport(): DiagnosticReport {
        val worldReports = mutableListOf<WorldDiagnosticReport>()

        for (worldName in enabledWorlds) {
            val world = Bukkit.getWorld(worldName) ?: continue
            val chunks = world.loadedChunks

            val playerCount = world.players.size

            // Calcular chunks esperados basado en view-distance
            val viewDistance = world.viewDistance
            val simulationDistance = try {
                world.simulationDistance
            } catch (e: Exception) {
                viewDistance
            }

            val expectedMinChunks = if (playerCount > 0) {
                val perPlayer = (viewDistance * 2 + 1) * (viewDistance * 2 + 1)
                perPlayer
            } else {
                0
            }

            val loadLevelCounts = mutableMapOf<String, Int>()
            for (chunk in chunks) {
                val level = getLoadLevelSafe(chunk)
                loadLevelCounts[level] = loadLevelCounts.getOrDefault(level, 0) + 1
            }

            worldReports.add(WorldDiagnosticReport(
                worldName = worldName,
                loadedChunks = chunks.size,
                expectedMinChunks = expectedMinChunks,
                playerCount = playerCount,
                viewDistance = viewDistance,
                simulationDistance = simulationDistance,
                loadLevelDistribution = loadLevelCounts,
                persistentUnloadFailures = 0 // No trackeamos esto en la version simplificada
            ))
        }

        // Recomendaciones
        val recommendations = generateRecommendations(worldReports)

        return DiagnosticReport(
            timestamp = System.currentTimeMillis(),
            worldReports = worldReports,
            recommendations = recommendations,
            paperTicketNote = "Using simplified approach: let Paper decide what can be unloaded."
        )
    }

    /**
     * Genera recomendaciones basadas en el diagnostico.
     */
    private fun generateRecommendations(worldReports: List<WorldDiagnosticReport>): List<String> {
        val recommendations = mutableListOf<String>()

        for (report in worldReports) {
            // Muchos chunks ENTITY_TICKING
            val entityTicking = report.loadLevelDistribution["ENTITY_TICKING"] ?: 0
            if (entityTicking > 500) {
                recommendations.add("[${report.worldName}] High ENTITY_TICKING chunks ($entityTicking). " +
                        "Consider reducing simulation-distance in server.properties")
            }

            // Chunks muy por encima de lo esperado
            if (report.loadedChunks > report.expectedMinChunks * 2 && report.playerCount > 0) {
                recommendations.add("[${report.worldName}] Loaded chunks (${report.loadedChunks}) " +
                        "significantly exceeds expected minimum (${report.expectedMinChunks}). " +
                        "Check for plugins adding chunk tickets or reduce view-distance")
            }
        }

        // Recomendaciones generales
        if (recommendations.isEmpty()) {
            recommendations.add("Chunk loading appears normal.")
        }

        recommendations.add("To reduce loaded chunks, adjust paper-world-defaults.yml:")
        recommendations.add("  chunks.delay-chunk-unloads-by: 5s (default 10s)")
        recommendations.add("And server.properties:")
        recommendations.add("  view-distance=8, simulation-distance=6")

        return recommendations
    }

    // ============================================================================
    // DATA CLASSES (Simplificadas)
    // ============================================================================

    /** Posicion de un chunk (solo X, Z) - usado para estadisticas */
    private data class ChunkPosition(val x: Int, val z: Int)

    /** Estadisticas de chunks de un mundo */
    data class ChunkStats(
        val total: Int,
        val spawnChunks: Int,
        val forceLoaded: Int,
        val nearPlayers: Int,
        val inactive: Int,
        val active: Int,
        val withPluginTickets: Int,
        val entityTicking: Int,
        val ticking: Int,
        val border: Int
    )

    /** Reporte de diagnostico de un mundo */
    data class WorldDiagnosticReport(
        val worldName: String,
        val loadedChunks: Int,
        val expectedMinChunks: Int,
        val playerCount: Int,
        val viewDistance: Int,
        val simulationDistance: Int,
        val loadLevelDistribution: Map<String, Int>,
        val persistentUnloadFailures: Int
    )

    /** Reporte de diagnostico completo */
    data class DiagnosticReport(
        val timestamp: Long,
        val worldReports: List<WorldDiagnosticReport>,
        val recommendations: List<String>,
        val paperTicketNote: String
    )
}
