package dev.srcodex.serverbooster.config

import dev.srcodex.serverbooster.ServerBoosterPlugin
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

class ConfigManager(private val plugin: ServerBoosterPlugin) {

    lateinit var entityLimiterConfig: EntityLimiterConfig
        private set
    lateinit var entityOptimizerConfig: EntityOptimizerConfig
        private set
    lateinit var hologramConfig: HologramConfig
        private set
    lateinit var chunkOptimizerConfig: ChunkOptimizerConfig
        private set
    lateinit var tpsCommandsConfig: TpsCommandsConfig
        private set

    var debug: Boolean = false
        private set

    fun loadAll() {
        // Save default configs if they don't exist
        saveDefaultConfigs()

        // Load each config
        entityLimiterConfig = loadEntityLimiterConfig()
        entityOptimizerConfig = loadEntityOptimizerConfig()
        hologramConfig = loadHologramConfig()
        chunkOptimizerConfig = loadChunkOptimizerConfig()
        tpsCommandsConfig = loadTpsCommandsConfig()

        debug = entityLimiterConfig.debug
    }

    private fun saveDefaultConfigs() {
        saveResourceIfNotExists("config_entity_limiter.yml")
        saveResourceIfNotExists("config_optimize_entities.yml")
        saveResourceIfNotExists("config_holo.yml")
        saveResourceIfNotExists("chunks_optimizer.yml")
        saveResourceIfNotExists("config_tps.yml")
        saveResourceIfNotExists("lang/en_us.yml")
        saveResourceIfNotExists("lang/es_es.yml")
        saveResourceIfNotExists("lang_limiter/en.yml")
    }

    private fun saveResourceIfNotExists(resourcePath: String) {
        val file = File(plugin.dataFolder, resourcePath)
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            plugin.saveResource(resourcePath, false)
        }
    }

    private fun getConfig(filename: String): YamlConfiguration {
        val file = File(plugin.dataFolder, filename)
        if (!file.exists()) {
            plugin.saveResource(filename, false)
        }
        return YamlConfiguration.loadConfiguration(file)
    }

    private fun loadEntityLimiterConfig(): EntityLimiterConfig {
        val config = getConfig("config_entity_limiter.yml")
        return EntityLimiterConfig(
            enabled = true,
            lang = config.getString("lang", "en") ?: "en",
            radius = config.getInt("radius", 56),
            growthTicks = config.getInt("growth_ticks", -1),
            relativeAge = config.getBoolean("relative_age", false),
            debug = config.getBoolean("debug", false),
            ageLimiterEnabled = config.getBoolean("age-limiter-enabled", false),
            breedingLimiterEnabled = config.getBoolean("breeding-limiter-enabled", false),
            sheepColorCheck = config.getBoolean("sheep-color-check", false),
            breedingTicks = config.getInt("breeding_ticks", -1),
            forceSpawnDeny = config.getBoolean("force-spawn-deny", true),
            cancelEventInsteadOfRemove = config.getBoolean("cancel-event-instead-of-remove-entity", true),
            keepSittingEntities = config.getBoolean("keep-sitting-entities", true),
            spawnWhitelist = config.getStringList("spawn-whitelist.types"),
            worlds = config.getStringList("worlds").toMutableList(),
            limits = loadEntityLimits(config),
            defaults = EntityLimitDefaults(
                age = config.getInt("defaults.age", -1),
                radiusMax = config.getInt("defaults.radius_max", 10),
                chunkMax = config.getInt("defaults.chunk_max", 5),
                cull = config.getInt("defaults.cull", 5)
            )
        )
    }

    private fun loadEntityLimits(config: YamlConfiguration): Map<String, EntityLimit> {
        val limits = mutableMapOf<String, EntityLimit>()
        val section = config.getConfigurationSection("limits") ?: return limits

        for (key in section.getKeys(false)) {
            val limitSection = section.getConfigurationSection(key) ?: continue
            limits[key.uppercase()] = EntityLimit(
                type = key.uppercase(),
                age = limitSection.getInt("age", -1),
                radiusMax = limitSection.getInt("radius_max", -1),
                chunkMax = limitSection.getInt("chunk_max", -1),
                cull = limitSection.getInt("cull", -1)
            )
        }

        return limits
    }

    private fun loadEntityOptimizerConfig(): EntityOptimizerConfig {
        val config = getConfig("config_optimize_entities.yml")
        return EntityOptimizerConfig(
            enabled = true,
            overrideSpigotBukkitConfigs = config.getBoolean("override_spigot_bukkit_configs", true),
            mobSpawnRange = config.getInt("mob-spawn-range", 3),
            entityActivationRange = EntityActivationRange(
                tickInactiveVillagers = config.getBoolean("entity-activation-range.tick-inactive-villagers", false),
                animals = config.getInt("entity-activation-range.animals", 16),
                monsters = config.getInt("entity-activation-range.monsters", 16),
                raiders = config.getInt("entity-activation-range.raiders", 25),
                misc = config.getInt("entity-activation-range.misc", 2)
            ),
            ticksPer = TicksPer(
                animalSpawns = config.getInt("ticks-per.animal-spawns", 70),
                monsterSpawns = config.getInt("ticks-per.monster-spawns", 6)
            ),
            spawnLimits = SpawnLimits(
                monsters = config.getInt("spawn-limits.monsters", 7),
                animals = config.getInt("spawn-limits.animals", 2),
                waterAnimals = config.getInt("spawn-limits.water-animals", 1),
                ambient = config.getInt("spawn-limits.ambient", 1)
            ),
            logToConsole = config.getBoolean("log-to-console", false),
            logDetailed = config.getBoolean("log-detailed", false),
            logTps = LogTps(
                enabled = config.getBoolean("log-tps.enabled", false),
                interval = config.getInt("log-tps.interval", 1200)
            ),
            disableTickForUntrackedEntities = config.getBoolean("disable-tick-for-untracked-entities", true),
            disableAiForUntrackedEntities = config.getBoolean("disable-ai-for-untracked-entities", true),
            fixAiPreviousUpdate = config.getBoolean("fix-ai-previous-update", false),
            triggerAlways = TriggerOption(
                enabled = config.getBoolean("trigger-options.always.enabled", true),
                untrackTicks = config.getInt("trigger-options.always.untrack-ticks", 600)
            ),
            triggerWhenTpsBelow = TriggerTpsOption(
                enabled = config.getBoolean("trigger-options.when-tps-below.enabled", true),
                value = config.getDouble("trigger-options.when-tps-below.value", 18.5),
                untrackTicks = config.getInt("trigger-options.when-tps-below.untrack-ticks", 450)
            ),
            checkUntrackedEntitiesFrequency = config.getInt("check-untracked-entities-frequency", 35),
            trackingRange = config.getInt("tracking-range", 35),
            ignore = IgnoreOptions(
                customNamed = config.getBoolean("ignore.custom-named", false),
                invulnerable = config.getBoolean("ignore.invulnerable", false),
                drops = config.getBoolean("ignore.drops", true),
                itemFrames = config.getBoolean("ignore.itemframes", true),
                armorStands = config.getBoolean("ignore.armorstands", true),
                villagers = config.getBoolean("ignore.villagers", false)
            ),
            worlds = config.getStringList("worlds").toMutableList()
        )
    }

    private fun loadHologramConfig(): HologramConfig {
        val config = getConfig("config_holo.yml")
        return HologramConfig(
            enabled = config.getBoolean("enabled", true),
            lang = config.getString("lang", "en_us") ?: "en_us",
            worlds = config.getStringList("worlds").toMutableList(),
            forceMerge = ForceMergeConfig(
                enabled = config.getBoolean("merge.force_merge.enabled", true),
                radius = config.getInt("merge.force_merge.radius", 7),
                preciseSimilarityCheck = config.getBoolean("merge.force_merge.more_precise_item_similarity_check__ENABLE_ONLY_IF_ANY_DUPE_PROBLEM", false),
                maxStack = config.getInt("merge.force_merge.max_stack.custom_amount", 512)
            ),
            mergeOnlyPlayerDrops = config.getBoolean("merge.merge_only_player_drops", false),
            hologram = HologramDisplay(
                enabled = config.getBoolean("merge.hologram.enabled", true),
                format = config.getString("merge.hologram.format", "&f{name} &bx{amount}") ?: "&f{name} &bx{amount}",
                glow = GlowConfig(
                    enabled = config.getBoolean("merge.hologram.glow.enabled", true),
                    color = config.getString("merge.hologram.glow.color", "AQUA") ?: "AQUA",
                    onlyPlayerDroppedItems = config.getBoolean("merge.hologram.glow.only_player_dropped_items", true)
                )
            ),
            blacklist = config.getStringList("merge.blacklist"),
            pickupDelay = PickupDelayConfig(
                legitStacks = config.getInt("pickup_delay.legit_stacks", 2),
                unstackableItemsStacks = config.getInt("pickup_delay.unstackable_items_stacks", 2)
            )
        )
    }

    private fun loadChunkOptimizerConfig(): ChunkOptimizerConfig {
        val config = getConfig("chunks_optimizer.yml")
        return ChunkOptimizerConfig(
            slowDownChunkPackets = SlowDownChunkPackets(
                whenTpsBelow = TpsThreshold(
                    enabled = config.getBoolean("slow-down-chunk-packets.when-tps-below.enabled", true),
                    value = config.getDouble("slow-down-chunk-packets.when-tps-below.value", 19.0)
                ),
                whenHighPlayerPing = PingThreshold(
                    enabled = config.getBoolean("slow-down-chunk-packets.when-high-player-ping.enabled", true),
                    value = config.getInt("slow-down-chunk-packets.when-high-player-ping.value", 150)
                ),
                worlds = config.getStringList("slow-down-chunk-packets.worlds")
            ),
            blockPhysicsDetectorEnabled = config.getBoolean("block-physics-lag-detector.enabled", true),
            blockPhysicsLowTps = config.getDouble("block-physics-lag-detector.low-tps", 18.0),
            blockPhysicsWarningThreshold = config.getInt("block-physics-lag-detector.lag.warning-threshold", 950000),
            blockPhysicsNotifyOp = config.getBoolean("block-physics-lag-detector.lag.notify-op", true),
            blockPhysicsCancelEvent = config.getBoolean("block-physics-lag-detector.lag.cancel-event", false),
            blockPhysicsWorlds = config.getStringList("block-physics-lag-detector.worlds"),
            unloadChunksEnabled = config.getBoolean("unload-chunks.enabled", true),
            unloadChunksInterval = config.getInt("unload-chunks.interval-ticks", 6000),
            unloadChunksLog = config.getBoolean("unload-chunks.log.unload", true),
            unloadChunksWorlds = config.getStringList("unload-chunks.worlds"),
            elytraNerfEnabled = config.getBoolean("optimize-elytra.reptide-trident-nerf.enabled", true) ||
                    config.getBoolean("optimize-elytra.firework-nerf.enabled", true),
            riptideTridentNerf = ElytraNerfConfig(
                enabled = config.getBoolean("optimize-elytra.reptide-trident-nerf.enabled", true),
                delay = config.getInt("optimize-elytra.reptide-trident-nerf.delay", 100)
            ),
            fireworkNerf = ElytraNerfConfig(
                enabled = config.getBoolean("optimize-elytra.firework-nerf.enabled", true),
                delay = config.getInt("optimize-elytra.firework-nerf.delay", 60)
            ),
            elytraLog = config.getBoolean("optimize-elytra.log", false),
            elytraWorlds = config.getStringList("optimize-elytra.worlds")
        )
    }

    private fun loadTpsCommandsConfig(): TpsCommandsConfig {
        val config = getConfig("config_tps.yml")
        val commandGroups = mutableListOf<TpsCommandGroup>()

        val commandsSection = config.getConfigurationSection("tps-commands.commands")
        if (commandsSection != null) {
            for (groupKey in commandsSection.getKeys(false)) {
                val groupSection = commandsSection.getConfigurationSection(groupKey) ?: continue
                val commands = mutableListOf<TpsCommand>()

                val listSection = groupSection.getConfigurationSection("list")
                if (listSection != null) {
                    for (cmdKey in listSection.getKeys(false)) {
                        val cmdSection = listSection.getConfigurationSection(cmdKey) ?: continue
                        commands.add(TpsCommand(
                            command = cmdSection.getString("command", "") ?: "",
                            delayTicks = cmdSection.getInt("delay_ticks", 0)
                        ))
                    }
                }

                commandGroups.add(TpsCommandGroup(
                    name = groupKey,
                    tps = groupSection.getDouble("tps", 16.0),
                    delayTicks = groupSection.getInt("delay_ticks", 36000),
                    commands = commands
                ))
            }
        }

        return TpsCommandsConfig(
            enabled = config.getBoolean("tps-commands.enabled", false),
            commandGroups = commandGroups
        )
    }
}
