package dev.srcodex.serverbooster.lang

import dev.srcodex.serverbooster.ServerBoosterPlugin
import org.bukkit.ChatColor
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

class LanguageManager(private val plugin: ServerBoosterPlugin) {

    private var messages: YamlConfiguration = YamlConfiguration()
    private var limiterMessages: YamlConfiguration = YamlConfiguration()

    init {
        reload()
    }

    fun reload() {
        // Load hologram/main language file
        val holoLang = plugin.configManager.hologramConfig.lang
        val holoFile = File(plugin.dataFolder, "lang/$holoLang.yml")
        if (!holoFile.exists()) {
            plugin.saveResource("lang/$holoLang.yml", false)
        }
        if (holoFile.exists()) {
            messages = YamlConfiguration.loadConfiguration(holoFile)
        }

        // Load entity limiter language file
        val limiterLang = plugin.configManager.entityLimiterConfig.lang
        val limiterFile = File(plugin.dataFolder, "lang_limiter/$limiterLang.yml")
        if (!limiterFile.exists()) {
            plugin.saveResource("lang_limiter/$limiterLang.yml", false)
        }
        if (limiterFile.exists()) {
            limiterMessages = YamlConfiguration.loadConfiguration(limiterFile)
        }
    }

    fun getMessage(key: String, vararg placeholders: Pair<String, Any>): String {
        var message = messages.getString(key, key) ?: key
        message = translateColors(message)
        for ((placeholder, value) in placeholders) {
            message = message.replace("{$placeholder}", value.toString())
        }
        return message
    }

    fun getLimiterMessage(key: String, vararg placeholders: Pair<String, Any>): String {
        var message = limiterMessages.getString(key, key) ?: key
        message = translateColors(message)
        for ((placeholder, value) in placeholders) {
            message = message.replace("{$placeholder}", value.toString())
        }
        return message
    }

    private fun translateColors(message: String): String {
        return ChatColor.translateAlternateColorCodes('&', message)
    }

    // Common messages
    val prefix: String
        get() = getMessage("prefix", "prefix" to "&8[&bServerBooster&8]&r ")

    val reloadSuccess: String
        get() = getMessage("reload-success", "reload-success" to "&aConfiguration reloaded successfully!")

    val noPermission: String
        get() = getMessage("no-permission", "no-permission" to "&cYou don't have permission to use this command.")

    val breedFailed: String
        get() = getLimiterMessage("breed-failed-too-many-mobs",
            "breed-failed-too-many-mobs" to "&cBreed failed. Too many mobs of this type in this area.")

    val chunkTooManyUpdates: String
        get() = getLimiterMessage("chunk-too-many-updates",
            "chunk-too-many-updates" to "&cToo many BlockPhysics updates near {coords}. May contain a lag machine.")
}
