package dev.srcodex.serverbooster.config

// Entity Limiter Config
data class EntityLimiterConfig(
    val enabled: Boolean,
    val lang: String,
    val radius: Int,
    val growthTicks: Int,
    val relativeAge: Boolean,
    val debug: Boolean,
    val ageLimiterEnabled: Boolean,
    val breedingLimiterEnabled: Boolean,
    val sheepColorCheck: Boolean,
    val breedingTicks: Int,
    val forceSpawnDeny: Boolean,
    val cancelEventInsteadOfRemove: Boolean,
    val keepSittingEntities: Boolean,
    val spawnWhitelist: List<String>,
    val worlds: MutableList<String>,
    val limits: Map<String, EntityLimit>,
    val defaults: EntityLimitDefaults
)

data class EntityLimit(
    val type: String,
    val age: Int,
    val radiusMax: Int,
    val chunkMax: Int,
    val cull: Int
)

data class EntityLimitDefaults(
    val age: Int,
    val radiusMax: Int,
    val chunkMax: Int,
    val cull: Int
)

// Entity Optimizer Config
data class EntityOptimizerConfig(
    val enabled: Boolean,
    val overrideSpigotBukkitConfigs: Boolean,
    val mobSpawnRange: Int,
    val entityActivationRange: EntityActivationRange,
    val ticksPer: TicksPer,
    val spawnLimits: SpawnLimits,
    val logToConsole: Boolean,
    val logDetailed: Boolean,
    val logTps: LogTps,
    val disableTickForUntrackedEntities: Boolean,
    val disableAiForUntrackedEntities: Boolean,
    val fixAiPreviousUpdate: Boolean,
    val triggerAlways: TriggerOption,
    val triggerWhenTpsBelow: TriggerTpsOption,
    val checkUntrackedEntitiesFrequency: Int,
    val trackingRange: Int,
    val ignore: IgnoreOptions,
    val worlds: MutableList<String>
)

data class EntityActivationRange(
    val tickInactiveVillagers: Boolean,
    val animals: Int,
    val monsters: Int,
    val raiders: Int,
    val misc: Int
)

data class TicksPer(
    val animalSpawns: Int,
    val monsterSpawns: Int
)

data class SpawnLimits(
    val monsters: Int,
    val animals: Int,
    val waterAnimals: Int,
    val ambient: Int
)

data class LogTps(
    val enabled: Boolean,
    val interval: Int
)

data class TriggerOption(
    val enabled: Boolean,
    val untrackTicks: Int
)

data class TriggerTpsOption(
    val enabled: Boolean,
    val value: Double,
    val untrackTicks: Int
)

data class IgnoreOptions(
    val customNamed: Boolean,
    val invulnerable: Boolean,
    val drops: Boolean,
    val itemFrames: Boolean,
    val armorStands: Boolean,
    val villagers: Boolean
)

// Hologram Config
data class HologramConfig(
    val enabled: Boolean,
    val lang: String,
    val worlds: MutableList<String>,
    val forceMerge: ForceMergeConfig,
    val mergeOnlyPlayerDrops: Boolean,
    val hologram: HologramDisplay,
    val blacklist: List<String>,
    val pickupDelay: PickupDelayConfig
)

data class ForceMergeConfig(
    val enabled: Boolean,
    val radius: Int,
    val preciseSimilarityCheck: Boolean,
    val maxStack: Int
)

data class HologramDisplay(
    val enabled: Boolean,
    val format: String,
    val glow: GlowConfig
)

data class GlowConfig(
    val enabled: Boolean,
    val color: String,
    val onlyPlayerDroppedItems: Boolean
)

data class PickupDelayConfig(
    val legitStacks: Int,
    val unstackableItemsStacks: Int
)

// Chunk Optimizer Config
data class ChunkOptimizerConfig(
    val slowDownChunkPackets: SlowDownChunkPackets,
    val blockPhysicsDetectorEnabled: Boolean,
    val blockPhysicsLowTps: Double,
    val blockPhysicsWarningThreshold: Int,
    val blockPhysicsNotifyOp: Boolean,
    val blockPhysicsCancelEvent: Boolean,
    val blockPhysicsWorlds: List<String>,
    val unloadChunksEnabled: Boolean,
    val unloadChunksInterval: Int,
    val unloadChunksLog: Boolean,
    val unloadChunksWorlds: List<String>,
    val elytraNerfEnabled: Boolean,
    val riptideTridentNerf: ElytraNerfConfig,
    val fireworkNerf: ElytraNerfConfig,
    val elytraLog: Boolean,
    val elytraWorlds: List<String>
)

data class SlowDownChunkPackets(
    val whenTpsBelow: TpsThreshold,
    val whenHighPlayerPing: PingThreshold,
    val worlds: List<String>
)

data class TpsThreshold(
    val enabled: Boolean,
    val value: Double
)

data class PingThreshold(
    val enabled: Boolean,
    val value: Int
)

data class ElytraNerfConfig(
    val enabled: Boolean,
    val delay: Int
)

// TPS Commands Config
data class TpsCommandsConfig(
    val enabled: Boolean,
    val commandGroups: List<TpsCommandGroup>
)

data class TpsCommandGroup(
    val name: String,
    val tps: Double,
    val delayTicks: Int,
    val commands: List<TpsCommand>
)

data class TpsCommand(
    val command: String,
    val delayTicks: Int
)
