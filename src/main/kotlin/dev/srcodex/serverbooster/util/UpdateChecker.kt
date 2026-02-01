package dev.srcodex.serverbooster.util

import dev.srcodex.serverbooster.ServerBoosterPlugin
import kotlinx.coroutines.*
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Update checker that uses the public GitHub API to check for new releases.
 * No authentication required - uses public endpoints only.
 */
class UpdateChecker(private val plugin: ServerBoosterPlugin) : Listener {

    companion object {
        private const val GITHUB_API_URL = "https://api.github.com/repos/SrCodexStudio/ServerBooster/releases/latest"
        private const val SPIGOT_RESOURCE_URL = "https://www.spigotmc.org/resources/serverbooster.XXXXX/"
        private const val GITHUB_RELEASES_URL = "https://github.com/SrCodexStudio/ServerBooster/releases"

        // Check interval: 6 hours
        private const val CHECK_INTERVAL_HOURS = 6L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var latestVersion: String? = null
    private var updateAvailable = false
    private var downloadUrl: String? = null
    private var releaseNotes: String? = null

    private val currentVersion: String = plugin.description.version

    // Message styling (matching plugin style)
    private val prefix = "${ChatColor.DARK_GRAY}[${ChatColor.AQUA}ServerBooster${ChatColor.DARK_GRAY}] "

    fun start() {
        // Register events for operator notifications
        Bukkit.getPluginManager().registerEvents(this, plugin)

        // Initial check after 10 seconds (let server start)
        scope.launch {
            delay(10_000)
            checkForUpdates()

            // Then check periodically
            while (isActive) {
                delay(TimeUnit.HOURS.toMillis(CHECK_INTERVAL_HOURS))
                checkForUpdates()
            }
        }

        plugin.logger.info("Update checker initialized")
    }

    fun shutdown() {
        HandlerList.unregisterAll(this)
        scope.cancel()
    }

    /**
     * Checks GitHub API for the latest release version.
     */
    private suspend fun checkForUpdates() {
        try {
            val response = fetchLatestRelease()
            if (response != null) {
                parseReleaseInfo(response)

                if (updateAvailable) {
                    notifyConsole()
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Could not check for updates: ${e.message}")
        }
    }

    /**
     * Fetches the latest release info from GitHub API.
     */
    private suspend fun fetchLatestRelease(): String? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null

        try {
            val url = URL(GITHUB_API_URL)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.setRequestProperty("User-Agent", "ServerBooster-UpdateChecker")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    reader.readText()
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Parses the GitHub API response to extract version info.
     * Simple JSON parsing without external libraries.
     */
    private fun parseReleaseInfo(json: String) {
        try {
            // Extract tag_name (version)
            val tagNameMatch = """"tag_name"\s*:\s*"([^"]+)"""".toRegex().find(json)
            val tagName = tagNameMatch?.groupValues?.get(1) ?: return

            // Remove 'v' prefix if present
            latestVersion = tagName.removePrefix("v")

            // Extract html_url (download page)
            val htmlUrlMatch = """"html_url"\s*:\s*"([^"]+)"""".toRegex().find(json)
            downloadUrl = htmlUrlMatch?.groupValues?.get(1) ?: GITHUB_RELEASES_URL

            // Extract body (release notes) - first 200 chars
            val bodyMatch = """"body"\s*:\s*"([^"]{0,500})"""".toRegex().find(json)
            releaseNotes = bodyMatch?.groupValues?.get(1)
                ?.replace("\\r\\n", " ")
                ?.replace("\\n", " ")
                ?.take(150)

            // Compare versions
            updateAvailable = isNewerVersion(latestVersion!!, currentVersion)

        } catch (e: Exception) {
            plugin.logger.warning("Failed to parse release info: ${e.message}")
        }
    }

    /**
     * Compares two version strings (e.g., "2.1.0" vs "2.0.0").
     * Returns true if remote version is newer than local.
     */
    private fun isNewerVersion(remote: String, local: String): Boolean {
        try {
            val remoteParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
            val localParts = local.split(".").map { it.toIntOrNull() ?: 0 }

            val maxLength = maxOf(remoteParts.size, localParts.size)

            for (i in 0 until maxLength) {
                val remotePart = remoteParts.getOrElse(i) { 0 }
                val localPart = localParts.getOrElse(i) { 0 }

                when {
                    remotePart > localPart -> return true
                    remotePart < localPart -> return false
                }
            }

            return false // Same version
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Notifies the console about the available update.
     * Uses ConsoleSender for proper color support in console.
     */
    private fun notifyConsole() {
        SchedulerUtil.runTask {
            val console = Bukkit.getConsoleSender()
            console.sendMessage("")
            console.sendMessage("$prefix${ChatColor.GREEN}${ChatColor.BOLD}Update Available!")
            console.sendMessage("")
            console.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.YELLOW}Current: ${ChatColor.RED}v$currentVersion")
            console.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.YELLOW}Latest:  ${ChatColor.GREEN}v$latestVersion")
            console.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.AQUA}Download: ${ChatColor.WHITE}$downloadUrl")
            console.sendMessage("")
        }
    }

    /**
     * Sends update notification to a player (must be OP).
     */
    private fun notifyPlayer(player: Player) {
        if (!updateAvailable) return
        if (!player.isOp && !player.hasPermission("serverbooster.admin")) return

        player.sendMessage("")
        player.sendMessage("$prefix${ChatColor.GREEN}${ChatColor.BOLD}Update Available!")
        player.sendMessage("")
        player.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.YELLOW}Current: ${ChatColor.RED}v$currentVersion")
        player.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.YELLOW}Latest:  ${ChatColor.GREEN}v$latestVersion")
        player.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.AQUA}Download: ${ChatColor.GRAY}$downloadUrl")
        player.sendMessage("")
    }

    /**
     * Event handler - notify operators when they join.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        if (!updateAvailable) return
        if (!player.isOp && !player.hasPermission("serverbooster.admin")) return

        // Delay notification slightly so it appears after other join messages
        SchedulerUtil.runTaskLater(60L) { // 3 seconds
            if (player.isOnline) {
                notifyPlayer(player)
            }
        }
    }

    /**
     * Force check for updates (can be called from command).
     */
    fun forceCheck(): String {
        scope.launch {
            checkForUpdates()
        }

        return if (updateAvailable) {
            "${ChatColor.GREEN}Update available: ${ChatColor.YELLOW}v$latestVersion"
        } else {
            "${ChatColor.GREEN}You are running the latest version (v$currentVersion)"
        }
    }

    /**
     * Gets the current update status.
     */
    fun getStatus(): UpdateStatus {
        return UpdateStatus(
            currentVersion = currentVersion,
            latestVersion = latestVersion,
            updateAvailable = updateAvailable,
            downloadUrl = downloadUrl
        )
    }

    data class UpdateStatus(
        val currentVersion: String,
        val latestVersion: String?,
        val updateAvailable: Boolean,
        val downloadUrl: String?
    )
}
