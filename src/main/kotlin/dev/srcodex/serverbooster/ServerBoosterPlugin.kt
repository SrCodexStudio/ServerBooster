package dev.srcodex.serverbooster

import dev.srcodex.serverbooster.chunk.BlockPhysicsDetector
import dev.srcodex.serverbooster.chunk.ChunkBlockLimiter
import dev.srcodex.serverbooster.chunk.ChunkOptimizer
import dev.srcodex.serverbooster.chunk.ElytraOptimizer
import dev.srcodex.serverbooster.commands.ServerBoosterCommand
import dev.srcodex.serverbooster.config.ConfigManager
import dev.srcodex.serverbooster.detection.DetectionManager
import dev.srcodex.serverbooster.entity.EntityLimiter
import dev.srcodex.serverbooster.hologram.HologramItemManager
import dev.srcodex.serverbooster.lang.LanguageManager
import dev.srcodex.serverbooster.nms.NMSManager
import dev.srcodex.serverbooster.optimizer.EntityOptimizer
import dev.srcodex.serverbooster.optimizer.TpsCommandExecutor
import dev.srcodex.serverbooster.util.MinecraftVersion
import dev.srcodex.serverbooster.util.SchedulerUtil
import dev.srcodex.serverbooster.util.UpdateChecker
import kotlinx.coroutines.*
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Level

class ServerBoosterPlugin : JavaPlugin() {

    companion object {
        lateinit var instance: ServerBoosterPlugin
            private set

        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        fun debug(message: String) {
            if (instance.configManager.debug) {
                instance.logger.info("[DEBUG] $message")
            }
        }
    }

    // Managers
    lateinit var configManager: ConfigManager
        private set
    lateinit var languageManager: LanguageManager
        private set
    lateinit var nmsManager: NMSManager
        private set

    // Modules
    var entityLimiter: EntityLimiter? = null
        private set
    var entityOptimizer: EntityOptimizer? = null
        private set
    var hologramItemManager: HologramItemManager? = null
        private set
    var blockPhysicsDetector: BlockPhysicsDetector? = null
        private set
    var chunkOptimizer: ChunkOptimizer? = null
        private set
    var elytraOptimizer: ElytraOptimizer? = null
        private set
    var tpsCommandExecutor: TpsCommandExecutor? = null
        private set
    var detectionManager: DetectionManager? = null
        private set
    var chunkBlockLimiter: ChunkBlockLimiter? = null
        private set
    var updateChecker: UpdateChecker? = null
        private set

    // Version flags
    val isPaper: Boolean by lazy {
        try {
            Class.forName("com.destroystokyo.paper.PaperConfig")
            true
        } catch (e: ClassNotFoundException) {
            try {
                Class.forName("io.papermc.paper.configuration.Configuration")
                true
            } catch (e2: ClassNotFoundException) {
                false
            }
        }
    }

    val isFolia: Boolean by lazy {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    override fun onEnable() {
        instance = this

        // Check version compatibility
        if (!MinecraftVersion.isSupported) {
            logger.severe("This version of Minecraft (${MinecraftVersion.currentVersion}) is not supported!")
            logger.severe("Supported versions: 1.13 - 1.21.x")
            server.pluginManager.disablePlugin(this)
            return
        }

        logger.info("Running on Minecraft ${MinecraftVersion.currentVersion}")
        logger.info("Server type: ${if (isFolia) "Folia" else if (isPaper) "Paper" else "Spigot"}")

        try {
            // Initialize configuration
            configManager = ConfigManager(this)
            configManager.loadAll()

            // Initialize language system
            languageManager = LanguageManager(this)

            // Initialize NMS manager
            nmsManager = NMSManager(this)
            if (!nmsManager.initialize()) {
                logger.warning("NMS initialization failed. Some features may not work.")
            }

            // Initialize modules based on config
            initializeModules()

            // Register commands
            getCommand("serverbooster")?.setExecutor(ServerBoosterCommand(this))

            // Override spigot/bukkit configs if enabled
            if (configManager.entityOptimizerConfig.overrideSpigotBukkitConfigs) {
                overrideServerConfigs()
            }

            logger.info("ServerBooster v${description.version} has been enabled!")
            logger.info("Modules enabled: ${getEnabledModules().joinToString(", ")}")

            // Initialize Update Checker (check for new versions on GitHub)
            updateChecker = UpdateChecker(this)
            updateChecker?.start()

        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Failed to enable ServerBooster", e)
            server.pluginManager.disablePlugin(this)
        }
    }

    override fun onDisable() {
        // Cancel all coroutines
        scope.cancel()

        // Cleanup modules
        entityOptimizer?.shutdown()
        hologramItemManager?.cleanup()
        chunkOptimizer?.shutdown()
        detectionManager?.shutdown()
        chunkBlockLimiter?.unregister()
        updateChecker?.shutdown()

        // Restore entities AI
        if (entityOptimizer != null && !isReloading()) {
            nmsManager.restoreAllEntities()
        }

        logger.info("ServerBooster has been disabled!")
    }

    private fun initializeModules() {
        // Entity Limiter
        if (configManager.entityLimiterConfig.enabled) {
            entityLimiter = EntityLimiter(this)
            logger.info("Entity Limiter module enabled")
        }

        // Entity Optimizer (requires 1.14+)
        if (MinecraftVersion.isAtLeast(1, 14) && configManager.entityOptimizerConfig.enabled) {
            entityOptimizer = EntityOptimizer(this)
            entityOptimizer?.start()
            logger.info("Entity Optimizer module enabled")
        }

        // Hologram Items
        if (configManager.hologramConfig.enabled) {
            hologramItemManager = HologramItemManager(this)
            logger.info("Hologram Items module enabled")
        }

        // Block Physics Detector
        if (configManager.chunkOptimizerConfig.blockPhysicsDetectorEnabled) {
            blockPhysicsDetector = BlockPhysicsDetector(this)
            logger.info("Block Physics Detector module enabled")
        }

        // Chunk Optimizer
        if (configManager.chunkOptimizerConfig.unloadChunksEnabled) {
            chunkOptimizer = ChunkOptimizer(this)
            logger.info("Chunk Optimizer module enabled")
        }

        // Elytra Optimizer (requires 1.9+)
        if (MinecraftVersion.isAtLeast(1, 9) && configManager.chunkOptimizerConfig.elytraNerfEnabled) {
            elytraOptimizer = ElytraOptimizer(this)
            logger.info("Elytra Optimizer module enabled")
        }

        // TPS Commands
        if (configManager.tpsCommandsConfig.enabled) {
            tpsCommandExecutor = TpsCommandExecutor(this)
            logger.info("TPS Commands module enabled")
        }

        // Detection Manager (always enabled for /sb detect command)
        detectionManager = DetectionManager(this)
        logger.info("Detection Manager module enabled")

        // Chunk Block Limiter
        if (configManager.chunkBlockLimitsConfig.enabled) {
            chunkBlockLimiter = ChunkBlockLimiter(this)
            logger.info("Chunk Block Limiter module enabled")
        }
    }

    private fun getEnabledModules(): List<String> {
        val modules = mutableListOf<String>()
        if (entityLimiter != null) modules.add("EntityLimiter")
        if (entityOptimizer != null) modules.add("EntityOptimizer")
        if (hologramItemManager != null) modules.add("HologramItems")
        if (blockPhysicsDetector != null) modules.add("BlockPhysicsDetector")
        if (chunkOptimizer != null) modules.add("ChunkOptimizer")
        if (elytraOptimizer != null) modules.add("ElytraOptimizer")
        if (tpsCommandExecutor != null) modules.add("TpsCommands")
        if (detectionManager != null) modules.add("DetectionManager")
        if (chunkBlockLimiter != null) modules.add("ChunkBlockLimiter")
        return modules
    }

    private fun overrideServerConfigs() {
        val config = configManager.entityOptimizerConfig

        for (world in Bukkit.getWorlds()) {
            if (!config.worlds.contains(world.name)) continue

            try {
                nmsManager.overrideWorldConfig(world, config)
                debug("Overrode config for world: ${world.name}")
            } catch (e: Exception) {
                logger.warning("Failed to override config for world ${world.name}: ${e.message}")
            }
        }

        logger.info("Override of spigot.yml and bukkit.yml configs completed")
    }

    fun reload() {
        logger.info("Reloading ServerBooster...")

        // Disable modules first
        try {
            entityLimiter?.unregister()
            entityOptimizer?.shutdown()
            hologramItemManager?.cleanup()
            blockPhysicsDetector?.unregister()
            chunkOptimizer?.shutdown()
            elytraOptimizer?.unregister()
            tpsCommandExecutor?.shutdown()
            detectionManager?.shutdown()
            chunkBlockLimiter?.unregister()
        } catch (e: Exception) {
            logger.warning("Error during module shutdown: ${e.message}")
        }

        // Reset all module references
        entityLimiter = null
        entityOptimizer = null
        hologramItemManager = null
        blockPhysicsDetector = null
        chunkOptimizer = null
        elytraOptimizer = null
        tpsCommandExecutor = null
        detectionManager = null
        chunkBlockLimiter = null

        // Reload all configurations
        configManager.loadAll()
        languageManager.reload()

        // Re-initialize all modules with new config values
        initializeModules()

        // Override configs again if enabled
        if (configManager.entityOptimizerConfig.overrideSpigotBukkitConfigs) {
            overrideServerConfigs()
        }

        logger.info("ServerBooster reloaded successfully!")
        logger.info("Active modules: ${getEnabledModules().joinToString(", ")}")
    }

    private fun isReloading(): Boolean {
        return System.getProperty("ServerBoosterReloaded", null) != null
    }

    fun getTps(): Double {
        return nmsManager.getTps()
    }
}
