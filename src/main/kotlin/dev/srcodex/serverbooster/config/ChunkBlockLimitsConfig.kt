package dev.srcodex.serverbooster.config

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

data class ChunkBlockLimitsConfig(
    val enabled: Boolean = true,
    val radius: Int = 56,
    val worlds: List<String> = listOf("world", "world_nether", "world_the_end"),
    val denyMessage: String = "&cYou cannot place more {block} in this area! &7(Limit: {limit})",
    val bypassPermission: String = "serverbooster.blocklimits.bypass",
    val debug: Boolean = false,
    val limits: Map<String, Int> = emptyMap(),
    val globalLimitsEnabled: Boolean = false,
    val globalDefaultLimit: Int = 120,
    val globalLimits: Map<String, Int> = emptyMap()
) {
    companion object {
        fun load(file: File): ChunkBlockLimitsConfig {
            if (!file.exists()) {
                return ChunkBlockLimitsConfig()
            }

            val yaml = YamlConfiguration.loadConfiguration(file)

            val limits = mutableMapOf<String, Int>()
            val limitsSection = yaml.getConfigurationSection("limits")
            limitsSection?.getKeys(false)?.forEach { key ->
                limits[key] = limitsSection.getInt(key, -1)
            }

            val globalLimits = mutableMapOf<String, Int>()
            val globalSection = yaml.getConfigurationSection("global-limits.limits")
            globalSection?.getKeys(false)?.forEach { key ->
                globalLimits[key] = globalSection.getInt(key, 120)
            }

            return ChunkBlockLimitsConfig(
                enabled = yaml.getBoolean("enabled", true),
                radius = yaml.getInt("radius", 56),
                worlds = yaml.getStringList("worlds").ifEmpty { listOf("world", "world_nether", "world_the_end") },
                denyMessage = yaml.getString("deny-message", "&cYou cannot place more {block} in this area! &7(Limit: {limit})") ?: "&cYou cannot place more {block} in this area! &7(Limit: {limit})",
                bypassPermission = yaml.getString("bypass-permission", "serverbooster.blocklimits.bypass") ?: "serverbooster.blocklimits.bypass",
                debug = yaml.getBoolean("debug", false),
                limits = limits,
                globalLimitsEnabled = yaml.getBoolean("global-limits.enabled", true),
                globalDefaultLimit = yaml.getInt("global-limits.default-limit", 120),
                globalLimits = globalLimits
            )
        }
    }
}
