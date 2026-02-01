package dev.srcodex.serverbooster.commands

import dev.srcodex.serverbooster.ServerBoosterPlugin
import kotlinx.coroutines.*
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class ServerBoosterCommand(private val plugin: ServerBoosterPlugin) : CommandExecutor, TabCompleter {

    private val prefix = "${ChatColor.GRAY}[${ChatColor.AQUA}ServerBooster${ChatColor.GRAY}]${ChatColor.RESET} "
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            showHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "reload" -> handleReload(sender)
            "count" -> handleCount(sender, args)
            "limits" -> handleLimits(sender)
            "check" -> handleCheck(sender, args)
            "optimize" -> handleOptimize(sender)
            "blockphysics" -> handleBlockPhysics(sender)
            "detect" -> handleDetect(sender, args)
            "tps" -> handleTps(sender)
            "info" -> handleInfo(sender)
            "update" -> handleUpdate(sender)
            "help" -> showHelp(sender)
            else -> {
                sender.sendMessage("${prefix}${ChatColor.RED}Unknown command. Use /sb help")
            }
        }

        return true
    }

    private fun showHelp(sender: CommandSender) {
        if (!sender.hasPermission("serverbooster.help")) {
            sender.sendMessage("${prefix}${ChatColor.RED}You don't have permission!")
            return
        }

        sender.sendMessage("")
        sender.sendMessage("  ${ChatColor.AQUA}${ChatColor.BOLD}ServerBooster ${ChatColor.GRAY}v${plugin.description.version}")
        sender.sendMessage("")
        sender.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.YELLOW}/sb reload ${ChatColor.GRAY}- Reload configuration")
        sender.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.YELLOW}/sb info ${ChatColor.GRAY}- Show plugin information")
        sender.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.YELLOW}/sb tps ${ChatColor.GRAY}- Show current TPS")
        sender.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.YELLOW}/sb count [world] ${ChatColor.GRAY}- Count entities")
        sender.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.YELLOW}/sb limits ${ChatColor.GRAY}- Show entity limits")
        sender.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.YELLOW}/sb check <player> ${ChatColor.GRAY}- Check entities near player")
        sender.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.YELLOW}/sb optimize ${ChatColor.GRAY}- Force optimization")
        sender.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.YELLOW}/sb blockphysics ${ChatColor.GRAY}- Block physics report")
        sender.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.YELLOW}/sb detect <type> ${ChatColor.GRAY}- Detect mechanisms")
        sender.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.YELLOW}/sb update ${ChatColor.GRAY}- Check for updates")
        sender.sendMessage("")
    }

    private fun handleReload(sender: CommandSender) {
        if (!sender.hasPermission("serverbooster.reload")) {
            sender.sendMessage("${prefix}${ChatColor.RED}You don't have permission!")
            return
        }

        plugin.reload()
        sender.sendMessage("${prefix}${ChatColor.GREEN}Configuration reloaded!")
    }

    private fun handleCount(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("serverbooster.mobs.count")) {
            sender.sendMessage("${prefix}${ChatColor.RED}You don't have permission!")
            return
        }

        val world = if (args.size > 1) {
            org.bukkit.Bukkit.getWorld(args[1])
        } else if (sender is Player) {
            sender.world
        } else {
            org.bukkit.Bukkit.getWorlds().firstOrNull()
        }

        if (world == null) {
            sender.sendMessage("${prefix}${ChatColor.RED}World not found!")
            return
        }

        val counts = mutableMapOf<String, Int>()
        for (entity in world.livingEntities) {
            val type = entity.type.name
            counts[type] = counts.getOrDefault(type, 0) + 1
        }

        sender.sendMessage("")
        sender.sendMessage("  ${ChatColor.GREEN}${ChatColor.BOLD}Entity Count ${ChatColor.GRAY}- ${world.name}")
        sender.sendMessage("")
        sender.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.GRAY}Total: ${ChatColor.WHITE}${world.livingEntities.size}")
        sender.sendMessage("")

        counts.entries
            .sortedByDescending { it.value }
            .take(15)
            .forEach { (type, count) ->
                sender.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.WHITE}$type: ${ChatColor.AQUA}$count")
            }

        if (counts.size > 15) {
            sender.sendMessage("  ${ChatColor.DARK_GRAY}... and ${counts.size - 15} more types")
        }
        sender.sendMessage("")
    }

    private fun handleLimits(sender: CommandSender) {
        if (!sender.hasPermission("serverbooster.mobs.limits")) {
            sender.sendMessage("${prefix}${ChatColor.RED}You don't have permission!")
            return
        }

        val config = plugin.configManager.entityLimiterConfig

        sender.sendMessage("")
        sender.sendMessage("  ${ChatColor.GOLD}${ChatColor.BOLD}Entity Limits")
        sender.sendMessage("")
        sender.sendMessage("  ${ChatColor.YELLOW}Defaults:")
        sender.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.GRAY}Radius Max: ${ChatColor.WHITE}${config.defaults.radiusMax}")
        sender.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.GRAY}Chunk Max: ${ChatColor.WHITE}${config.defaults.chunkMax}")
        sender.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.GRAY}Cull: ${ChatColor.WHITE}${config.defaults.cull}")
        sender.sendMessage("")
        sender.sendMessage("  ${ChatColor.YELLOW}Custom Limits: ${ChatColor.WHITE}${config.limits.size} types")

        config.limits.entries.take(10).forEach { (type, limit) ->
            sender.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.WHITE}$type: ${ChatColor.GRAY}r=${ChatColor.AQUA}${limit.radiusMax}${ChatColor.DARK_GRAY}, ${ChatColor.GRAY}c=${ChatColor.AQUA}${limit.chunkMax}${ChatColor.DARK_GRAY}, ${ChatColor.GRAY}cull=${ChatColor.AQUA}${limit.cull}")
        }

        if (config.limits.size > 10) {
            sender.sendMessage("  ${ChatColor.DARK_GRAY}... and ${config.limits.size - 10} more")
        }
        sender.sendMessage("")
    }

    private fun handleCheck(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("serverbooster.mobs.check")) {
            sender.sendMessage("${prefix}${ChatColor.RED}You don't have permission!")
            return
        }

        val target: Player = if (args.size > 1) {
            org.bukkit.Bukkit.getPlayer(args[1]) ?: run {
                sender.sendMessage("${prefix}${ChatColor.RED}Player not found!")
                return
            }
        } else if (sender is Player) {
            sender
        } else {
            sender.sendMessage("${prefix}${ChatColor.RED}Specify a player!")
            return
        }

        val chunk = target.location.chunk
        val entityLimiter = plugin.entityLimiter

        if (entityLimiter == null) {
            sender.sendMessage("${prefix}${ChatColor.RED}Entity Limiter is not enabled!")
            return
        }

        sender.sendMessage("")
        sender.sendMessage("  ${ChatColor.LIGHT_PURPLE}${ChatColor.BOLD}Entity Check ${ChatColor.GRAY}- ${target.name}")
        sender.sendMessage("")
        sender.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.GRAY}Chunk: ${ChatColor.WHITE}${chunk.x}, ${chunk.z}")
        sender.sendMessage("")

        val chunkCounts = entityLimiter.getEntityCountInChunk(chunk)
        sender.sendMessage("  ${ChatColor.YELLOW}In Chunk:")
        chunkCounts.entries.take(10).forEach { (type, count) ->
            sender.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.WHITE}$type: ${ChatColor.AQUA}$count")
        }

        val radius = plugin.configManager.entityLimiterConfig.radius
        val radiusCounts = entityLimiter.getEntityCountInRadius(target.location, radius)
        sender.sendMessage("")
        sender.sendMessage("  ${ChatColor.YELLOW}In Radius ${ChatColor.GRAY}($radius blocks):")
        radiusCounts.entries.take(10).forEach { (type, count) ->
            sender.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.WHITE}$type: ${ChatColor.AQUA}$count")
        }
        sender.sendMessage("")
    }

    private fun handleOptimize(sender: CommandSender) {
        if (!sender.hasPermission("serverbooster.optimize")) {
            sender.sendMessage("${prefix}${ChatColor.RED}You don't have permission!")
            return
        }

        val optimizer = plugin.entityOptimizer
        if (optimizer == null) {
            sender.sendMessage("${prefix}${ChatColor.RED}Entity Optimizer is not enabled!")
            return
        }

        sender.sendMessage("${prefix}${ChatColor.YELLOW}Running optimization...")
        val count = optimizer.forceOptimize()
        sender.sendMessage("${prefix}${ChatColor.GREEN}Optimized $count entities!")
    }

    private fun handleBlockPhysics(sender: CommandSender) {
        if (!sender.hasPermission("serverbooster.chunks.blockphysics")) {
            sender.sendMessage("${prefix}${ChatColor.RED}You don't have permission!")
            return
        }

        val detector = plugin.blockPhysicsDetector
        if (detector == null) {
            sender.sendMessage("${prefix}${ChatColor.RED}Block Physics Detector is not enabled!")
            return
        }

        sender.sendMessage("")
        sender.sendMessage("  ${ChatColor.RED}${ChatColor.BOLD}Block Physics Report")
        sender.sendMessage("")
        sender.sendMessage(detector.getReport())
        sender.sendMessage("")
    }

    private fun handleDetect(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("serverbooster.detect")) {
            sender.sendMessage("${prefix}${ChatColor.RED}You don't have permission!")
            return
        }

        val detectionManager = plugin.detectionManager
        if (detectionManager == null) {
            sender.sendMessage("${prefix}${ChatColor.RED}Detection Manager is not enabled!")
            return
        }

        if (args.size < 2) {
            sender.sendMessage("")
            sender.sendMessage("  ${ChatColor.AQUA}${ChatColor.BOLD}Detection Usage")
            sender.sendMessage("")
            sender.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.YELLOW}/sb detect spawners ${ChatColor.GRAY}- Detect all spawners")
            sender.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.YELLOW}/sb detect spawners <type> ${ChatColor.GRAY}- Filter by type")
            sender.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.YELLOW}/sb detect redstone ${ChatColor.GRAY}- Detect redstone")
            sender.sendMessage("")
            return
        }

        when (args[1].lowercase()) {
            "spawners", "spawner" -> {
                val entityType = if (args.size > 2) args[2] else null
                handleDetectSpawners(sender, detectionManager, entityType)
            }
            "redstone", "red" -> {
                handleDetectRedstone(sender, detectionManager)
            }
            else -> {
                sender.sendMessage("${prefix}${ChatColor.RED}Unknown detection type. Use: spawners, redstone")
            }
        }
    }

    private fun handleDetectSpawners(
        sender: CommandSender,
        detectionManager: dev.srcodex.serverbooster.detection.DetectionManager,
        entityTypeName: String?
    ) {
        sender.sendMessage("${prefix}${ChatColor.YELLOW}Scanning for spawners...")

        scope.launch {
            try {
                val result = if (entityTypeName != null) {
                    if (!detectionManager.isValidEntityType(entityTypeName)) {
                        withContext(Dispatchers.Default) {
                            org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable {
                                sender.sendMessage("${prefix}${ChatColor.RED}Invalid entity type: $entityTypeName")
                                sender.sendMessage("${ChatColor.GRAY}Common types: ${detectionManager.getCommonSpawnerTypes().joinToString(", ")}")
                            })
                        }
                        return@launch
                    }
                    detectionManager.detectSpawnersByType(entityTypeName)
                } else {
                    detectionManager.detectSpawners()
                }

                // Send results on main thread
                org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (result.items.isEmpty()) {
                        sender.sendMessage("${prefix}${ChatColor.YELLOW}No spawners found near online players.")
                        return@Runnable
                    }

                    val typeFilter = if (entityTypeName != null) " ${ChatColor.GRAY}- $entityTypeName" else ""
                    sender.sendMessage("")
                    sender.sendMessage("  ${ChatColor.LIGHT_PURPLE}${ChatColor.BOLD}Spawner Detection$typeFilter")
                    sender.sendMessage("")
                    sender.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.GRAY}Players scanned: ${ChatColor.WHITE}${result.playersScanned} ${ChatColor.DARK_GRAY}| ${ChatColor.GRAY}Time: ${ChatColor.WHITE}${result.scanTimeMs}ms")
                    sender.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.GRAY}Found: ${ChatColor.GREEN}${result.items.size} spawners")
                    sender.sendMessage("")

                    result.items.take(15).forEachIndexed { index, spawner ->
                        val statusColor = if (spawner.isActive) ChatColor.GREEN else ChatColor.GRAY
                        val statusText = if (spawner.isActive) "ACTIVE" else "inactive"

                        sender.sendMessage("  ${ChatColor.AQUA}${index + 1}. ${ChatColor.WHITE}${spawner.entityType.name} $statusColor[$statusText]")
                        sender.sendMessage("     ${ChatColor.DARK_GRAY}- ${ChatColor.GRAY}Location: ${ChatColor.WHITE}${spawner.worldName}: ${spawner.x}, ${spawner.y}, ${spawner.z}")
                        sender.sendMessage("     ${ChatColor.DARK_GRAY}- ${ChatColor.GRAY}Entities nearby: ${ChatColor.YELLOW}${spawner.nearbyEntityCount}")

                        if (spawner.nearbyPlayers.isNotEmpty()) {
                            val playerList = spawner.nearbyPlayers.take(3).joinToString("${ChatColor.DARK_GRAY}, ${ChatColor.WHITE}") {
                                "${it.playerName} ${ChatColor.GRAY}(${String.format("%.1f", it.distance)}m)${ChatColor.WHITE}"
                            }
                            sender.sendMessage("     ${ChatColor.DARK_GRAY}- ${ChatColor.GRAY}Players: ${ChatColor.WHITE}$playerList")
                        }

                        sender.sendMessage("     ${ChatColor.GOLD}${detectionManager.getTeleportCommand(spawner.location)}")
                    }

                    if (result.items.size > 15) {
                        sender.sendMessage("")
                        sender.sendMessage("  ${ChatColor.DARK_GRAY}... and ${result.items.size - 15} more spawners")
                    }
                    sender.sendMessage("")
                })
            } catch (e: Exception) {
                org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable {
                    sender.sendMessage("${prefix}${ChatColor.RED}Error during detection: ${e.message}")
                })
            }
        }
    }

    private fun handleDetectRedstone(
        sender: CommandSender,
        detectionManager: dev.srcodex.serverbooster.detection.DetectionManager
    ) {
        sender.sendMessage("${prefix}${ChatColor.YELLOW}Scanning for redstone mechanisms...")

        scope.launch {
            try {
                val result = detectionManager.detectRedstone()

                // Send results on main thread
                org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (result.items.isEmpty()) {
                        sender.sendMessage("${prefix}${ChatColor.YELLOW}No significant redstone mechanisms found near online players.")
                        return@Runnable
                    }

                    sender.sendMessage("")
                    sender.sendMessage("  ${ChatColor.RED}${ChatColor.BOLD}Redstone Detection")
                    sender.sendMessage("")
                    sender.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.GRAY}Players scanned: ${ChatColor.WHITE}${result.playersScanned} ${ChatColor.DARK_GRAY}| ${ChatColor.GRAY}Time: ${ChatColor.WHITE}${result.scanTimeMs}ms")
                    sender.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.GRAY}Found: ${ChatColor.YELLOW}${result.items.size} redstone areas")
                    sender.sendMessage("")

                    result.items.take(15).forEachIndexed { index, redstone ->
                        val densityColor = when {
                            redstone.density >= 50 -> ChatColor.RED
                            redstone.density >= 25 -> ChatColor.YELLOW
                            else -> ChatColor.GREEN
                        }
                        val powerStatus = if (redstone.isPowered) "${ChatColor.RED}POWERED" else "${ChatColor.GRAY}inactive"

                        sender.sendMessage("  ${ChatColor.AQUA}${index + 1}. ${ChatColor.WHITE}${redstone.type.name} $powerStatus")
                        sender.sendMessage("     ${ChatColor.DARK_GRAY}- ${ChatColor.GRAY}Location: ${ChatColor.WHITE}${redstone.worldName}: ${redstone.x}, ${redstone.y}, ${redstone.z}")
                        sender.sendMessage("     ${ChatColor.DARK_GRAY}- ${ChatColor.GRAY}Density: $densityColor${redstone.density} components nearby")

                        if (redstone.nearbyPlayers.isNotEmpty()) {
                            val playerList = redstone.nearbyPlayers.take(3).joinToString("${ChatColor.DARK_GRAY}, ${ChatColor.WHITE}") {
                                "${it.playerName} ${ChatColor.GRAY}(${String.format("%.1f", it.distance)}m)${ChatColor.WHITE}"
                            }
                            sender.sendMessage("     ${ChatColor.DARK_GRAY}- ${ChatColor.GRAY}Players: ${ChatColor.WHITE}$playerList")
                        }

                        sender.sendMessage("     ${ChatColor.GOLD}${detectionManager.getTeleportCommand(redstone.location)}")
                    }

                    if (result.items.size > 15) {
                        sender.sendMessage("")
                        sender.sendMessage("  ${ChatColor.DARK_GRAY}... and ${result.items.size - 15} more locations")
                    }

                    sender.sendMessage("")
                    sender.sendMessage("  ${ChatColor.YELLOW}Tip: ${ChatColor.GRAY}High density areas may cause lag!")
                    sender.sendMessage("")
                })
            } catch (e: Exception) {
                org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable {
                    sender.sendMessage("${prefix}${ChatColor.RED}Error during detection: ${e.message}")
                })
            }
        }
    }

    private fun handleTps(sender: CommandSender) {
        if (!sender.hasPermission("serverbooster.tps")) {
            sender.sendMessage("${prefix}${ChatColor.RED}You don't have permission!")
            return
        }

        val tps = plugin.getTps()
        val color = when {
            tps >= 18 -> ChatColor.GREEN
            tps >= 15 -> ChatColor.YELLOW
            else -> ChatColor.RED
        }
        sender.sendMessage("")
        sender.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.GRAY}Current TPS: $color${String.format("%.2f", tps)}")
        sender.sendMessage("")
    }

    private fun handleInfo(sender: CommandSender) {
        if (!sender.hasPermission("serverbooster.info")) {
            sender.sendMessage("${prefix}${ChatColor.RED}You don't have permission!")
            return
        }

        val tps = plugin.getTps()
        val tpsColor = when {
            tps >= 18 -> ChatColor.GREEN
            tps >= 15 -> ChatColor.YELLOW
            else -> ChatColor.RED
        }

        sender.sendMessage("")
        sender.sendMessage("  ${ChatColor.AQUA}${ChatColor.BOLD}ServerBooster Info")
        sender.sendMessage("")
        sender.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.GRAY}Version: ${ChatColor.WHITE}${plugin.description.version}")
        sender.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.GRAY}Server: ${ChatColor.WHITE}${if (plugin.isFolia) "Folia" else if (plugin.isPaper) "Paper" else "Spigot"}")
        sender.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.GRAY}TPS: $tpsColor${String.format("%.2f", tps)}")
        sender.sendMessage("")
        sender.sendMessage("  ${ChatColor.YELLOW}Modules:")

        val modules = listOf(
            "EntityLimiter" to (plugin.entityLimiter != null),
            "EntityOptimizer" to (plugin.entityOptimizer != null),
            "HologramItems" to (plugin.hologramItemManager != null),
            "BlockPhysicsDetector" to (plugin.blockPhysicsDetector != null),
            "ChunkOptimizer" to (plugin.chunkOptimizer != null),
            "ElytraOptimizer" to (plugin.elytraOptimizer != null),
            "TpsCommands" to (plugin.tpsCommandExecutor != null),
            "ChunkBlockLimiter" to (plugin.chunkBlockLimiter != null)
        )

        for ((name, enabled) in modules) {
            val status = if (enabled) "${ChatColor.GREEN}✓" else "${ChatColor.RED}✗"
            sender.sendMessage("  ${ChatColor.DARK_GRAY}- $status ${ChatColor.WHITE}$name")
        }

        if (plugin.entityOptimizer != null) {
            sender.sendMessage("")
            sender.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.GRAY}Optimized Entities: ${ChatColor.AQUA}${plugin.entityOptimizer!!.getOptimizedCount()}")
        }
        sender.sendMessage("")
    }

    private fun handleUpdate(sender: CommandSender) {
        if (!sender.hasPermission("serverbooster.admin")) {
            sender.sendMessage("${prefix}${ChatColor.RED}You don't have permission!")
            return
        }

        val updateChecker = plugin.updateChecker
        if (updateChecker == null) {
            sender.sendMessage("${prefix}${ChatColor.RED}Update checker is not available!")
            return
        }

        sender.sendMessage("${prefix}${ChatColor.YELLOW}Checking for updates...")

        scope.launch {
            // Force check
            val result = updateChecker.forceCheck()

            // Get status after check
            kotlinx.coroutines.delay(2000) // Wait for async check

            val status = updateChecker.getStatus()

            dev.srcodex.serverbooster.util.SchedulerUtil.runTask {
                if (status.updateAvailable && status.latestVersion != null) {
                    sender.sendMessage("")
                    sender.sendMessage("  ${ChatColor.GREEN}${ChatColor.BOLD}Update Available!")
                    sender.sendMessage("")
                    sender.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.YELLOW}Current: ${ChatColor.RED}v${status.currentVersion}")
                    sender.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.YELLOW}Latest:  ${ChatColor.GREEN}v${status.latestVersion}")
                    sender.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.AQUA}Download: ${ChatColor.GRAY}${status.downloadUrl ?: "https://github.com/SrCodexStudio/ServerBooster/releases"}")
                    sender.sendMessage("")
                } else {
                    sender.sendMessage("")
                    sender.sendMessage("  ${ChatColor.GREEN}${ChatColor.BOLD}Up to Date!")
                    sender.sendMessage("")
                    sender.sendMessage("  ${ChatColor.DARK_GRAY}- ${ChatColor.GRAY}You are running the latest version ${ChatColor.WHITE}(v${status.currentVersion})")
                    sender.sendMessage("")
                }
            }
        }
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (args.size == 1) {
            return listOf("reload", "info", "tps", "count", "limits", "check", "optimize", "blockphysics", "detect", "update", "help")
                .filter { it.startsWith(args[0].lowercase()) }
        }

        if (args.size == 2) {
            when (args[0].lowercase()) {
                "count" -> {
                    return org.bukkit.Bukkit.getWorlds()
                        .map { it.name }
                        .filter { it.lowercase().startsWith(args[1].lowercase()) }
                }
                "check" -> {
                    return org.bukkit.Bukkit.getOnlinePlayers()
                        .map { it.name }
                        .filter { it.lowercase().startsWith(args[1].lowercase()) }
                }
                "detect" -> {
                    return listOf("spawners", "redstone")
                        .filter { it.startsWith(args[1].lowercase()) }
                }
            }
        }

        if (args.size == 3) {
            when (args[0].lowercase()) {
                "detect" -> {
                    if (args[1].lowercase() == "spawners" || args[1].lowercase() == "spawner") {
                        val commonTypes = plugin.detectionManager?.getCommonSpawnerTypes() ?: emptyList()
                        return commonTypes.filter { it.startsWith(args[2].lowercase()) }
                    }
                }
            }
        }

        return emptyList()
    }
}
