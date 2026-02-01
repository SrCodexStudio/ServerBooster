package dev.srcodex.serverbooster.commands

import dev.srcodex.serverbooster.ServerBoosterPlugin
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class ServerBoosterCommand(private val plugin: ServerBoosterPlugin) : CommandExecutor, TabCompleter {

    private val prefix = "${ChatColor.GRAY}[${ChatColor.AQUA}ServerBooster${ChatColor.GRAY}]${ChatColor.RESET} "

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
            "tps" -> handleTps(sender)
            "info" -> handleInfo(sender)
            "help" -> showHelp(sender)
            else -> {
                sender.sendMessage("${prefix}${ChatColor.RED}Unknown command. Use /sb help")
            }
        }

        return true
    }

    private fun showHelp(sender: CommandSender) {
        sender.sendMessage("${ChatColor.AQUA}═══════ ${ChatColor.WHITE}ServerBooster v${plugin.description.version} ${ChatColor.AQUA}═══════")
        sender.sendMessage("")
        sender.sendMessage("${ChatColor.YELLOW}/sb reload ${ChatColor.GRAY}- Reload configuration")
        sender.sendMessage("${ChatColor.YELLOW}/sb info ${ChatColor.GRAY}- Show plugin information")
        sender.sendMessage("${ChatColor.YELLOW}/sb tps ${ChatColor.GRAY}- Show current TPS")
        sender.sendMessage("${ChatColor.YELLOW}/sb count [world] ${ChatColor.GRAY}- Count entities")
        sender.sendMessage("${ChatColor.YELLOW}/sb limits ${ChatColor.GRAY}- Show entity limits")
        sender.sendMessage("${ChatColor.YELLOW}/sb check <player> ${ChatColor.GRAY}- Check entities near player")
        sender.sendMessage("${ChatColor.YELLOW}/sb optimize ${ChatColor.GRAY}- Force entity optimization")
        sender.sendMessage("${ChatColor.YELLOW}/sb blockphysics ${ChatColor.GRAY}- Show block physics report")
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

        sender.sendMessage("${ChatColor.AQUA}═══════ ${ChatColor.WHITE}Entity Count: ${world.name} ${ChatColor.AQUA}═══════")
        sender.sendMessage("${ChatColor.YELLOW}Total: ${ChatColor.WHITE}${world.livingEntities.size}")
        sender.sendMessage("")

        counts.entries
            .sortedByDescending { it.value }
            .take(15)
            .forEach { (type, count) ->
                sender.sendMessage("${ChatColor.GRAY}- ${ChatColor.WHITE}$type: ${ChatColor.AQUA}$count")
            }

        if (counts.size > 15) {
            sender.sendMessage("${ChatColor.GRAY}... and ${counts.size - 15} more types")
        }
    }

    private fun handleLimits(sender: CommandSender) {
        if (!sender.hasPermission("serverbooster.mobs.limits")) {
            sender.sendMessage("${prefix}${ChatColor.RED}You don't have permission!")
            return
        }

        val config = plugin.configManager.entityLimiterConfig

        sender.sendMessage("${ChatColor.AQUA}═══════ ${ChatColor.WHITE}Entity Limits ${ChatColor.AQUA}═══════")
        sender.sendMessage("")
        sender.sendMessage("${ChatColor.YELLOW}Defaults:")
        sender.sendMessage("${ChatColor.GRAY}  Radius Max: ${ChatColor.WHITE}${config.defaults.radiusMax}")
        sender.sendMessage("${ChatColor.GRAY}  Chunk Max: ${ChatColor.WHITE}${config.defaults.chunkMax}")
        sender.sendMessage("${ChatColor.GRAY}  Cull: ${ChatColor.WHITE}${config.defaults.cull}")
        sender.sendMessage("")
        sender.sendMessage("${ChatColor.YELLOW}Custom Limits: ${ChatColor.WHITE}${config.limits.size} types")

        config.limits.entries.take(10).forEach { (type, limit) ->
            sender.sendMessage("${ChatColor.GRAY}  $type: r=${limit.radiusMax}, c=${limit.chunkMax}, cull=${limit.cull}")
        }

        if (config.limits.size > 10) {
            sender.sendMessage("${ChatColor.GRAY}  ... and ${config.limits.size - 10} more")
        }
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

        sender.sendMessage("${ChatColor.AQUA}═══════ ${ChatColor.WHITE}Entity Check: ${target.name} ${ChatColor.AQUA}═══════")
        sender.sendMessage("")
        sender.sendMessage("${ChatColor.YELLOW}Chunk: ${ChatColor.WHITE}${chunk.x}, ${chunk.z}")

        val chunkCounts = entityLimiter.getEntityCountInChunk(chunk)
        sender.sendMessage("${ChatColor.YELLOW}In Chunk:")
        chunkCounts.entries.take(10).forEach { (type, count) ->
            sender.sendMessage("${ChatColor.GRAY}  $type: ${ChatColor.WHITE}$count")
        }

        val radius = plugin.configManager.entityLimiterConfig.radius
        val radiusCounts = entityLimiter.getEntityCountInRadius(target.location, radius)
        sender.sendMessage("")
        sender.sendMessage("${ChatColor.YELLOW}In Radius ($radius blocks):")
        radiusCounts.entries.take(10).forEach { (type, count) ->
            sender.sendMessage("${ChatColor.GRAY}  $type: ${ChatColor.WHITE}$count")
        }
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

        sender.sendMessage("${ChatColor.AQUA}═══════ ${ChatColor.WHITE}Block Physics Report ${ChatColor.AQUA}═══════")
        sender.sendMessage("")
        sender.sendMessage(detector.getReport())
    }

    private fun handleTps(sender: CommandSender) {
        val tps = plugin.getTps()
        val color = when {
            tps >= 18 -> ChatColor.GREEN
            tps >= 15 -> ChatColor.YELLOW
            else -> ChatColor.RED
        }
        sender.sendMessage("${prefix}${ChatColor.WHITE}TPS: $color${String.format("%.2f", tps)}")
    }

    private fun handleInfo(sender: CommandSender) {
        sender.sendMessage("${ChatColor.AQUA}═══════ ${ChatColor.WHITE}ServerBooster Info ${ChatColor.AQUA}═══════")
        sender.sendMessage("")
        sender.sendMessage("${ChatColor.YELLOW}Version: ${ChatColor.WHITE}${plugin.description.version}")
        sender.sendMessage("${ChatColor.YELLOW}Server: ${ChatColor.WHITE}${if (plugin.isFolia) "Folia" else if (plugin.isPaper) "Paper" else "Spigot"}")
        sender.sendMessage("${ChatColor.YELLOW}TPS: ${ChatColor.WHITE}${String.format("%.2f", plugin.getTps())}")
        sender.sendMessage("")
        sender.sendMessage("${ChatColor.YELLOW}Modules:")

        val modules = listOf(
            "EntityLimiter" to (plugin.entityLimiter != null),
            "EntityOptimizer" to (plugin.entityOptimizer != null),
            "HologramItems" to (plugin.hologramItemManager != null),
            "BlockPhysicsDetector" to (plugin.blockPhysicsDetector != null),
            "ChunkOptimizer" to (plugin.chunkOptimizer != null),
            "ElytraOptimizer" to (plugin.elytraOptimizer != null),
            "TpsCommands" to (plugin.tpsCommandExecutor != null)
        )

        for ((name, enabled) in modules) {
            val status = if (enabled) "${ChatColor.GREEN}✓" else "${ChatColor.RED}✗"
            sender.sendMessage("${ChatColor.GRAY}  $status ${ChatColor.WHITE}$name")
        }

        if (plugin.entityOptimizer != null) {
            sender.sendMessage("")
            sender.sendMessage("${ChatColor.YELLOW}Optimized Entities: ${ChatColor.WHITE}${plugin.entityOptimizer!!.getOptimizedCount()}")
        }
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (args.size == 1) {
            return listOf("reload", "info", "tps", "count", "limits", "check", "optimize", "blockphysics", "help")
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
            }
        }

        return emptyList()
    }
}
