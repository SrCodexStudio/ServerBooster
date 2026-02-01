package dev.srcodex.serverbooster.optimizer

import dev.srcodex.serverbooster.ServerBoosterPlugin
import dev.srcodex.serverbooster.config.TpsCommandGroup
import dev.srcodex.serverbooster.util.SchedulerUtil
import org.bukkit.Bukkit
import java.util.concurrent.ConcurrentHashMap

class TpsCommandExecutor(private val plugin: ServerBoosterPlugin) {

    private val config get() = plugin.configManager.tpsCommandsConfig
    private val lastExecution = ConcurrentHashMap<String, Long>()
    private var checkTask: Any? = null

    init {
        startChecking()
    }

    private fun startChecking() {
        // Check TPS every 20 ticks (1 second)
        checkTask = SchedulerUtil.runTaskTimer(20L, 20L) {
            checkAndExecute()
        }
    }

    fun shutdown() {
        SchedulerUtil.cancelTask(checkTask)
    }

    private fun checkAndExecute() {
        val currentTps = plugin.getTps()
        val currentTick = Bukkit.getCurrentTick()

        for (group in config.commandGroups) {
            // Check if TPS is below threshold
            if (currentTps >= group.tps) continue

            // Check cooldown
            val lastRun = lastExecution[group.name] ?: 0L
            if (currentTick - lastRun < group.delayTicks) continue

            // Execute commands
            executeCommandGroup(group, currentTps)
            lastExecution[group.name] = currentTick.toLong()
        }
    }

    private fun executeCommandGroup(group: TpsCommandGroup, tps: Double) {
        for (command in group.commands) {
            val cmd = command.command.replace("{tps}", String.format("%.2f", tps))

            if (command.delayTicks <= 0) {
                executeCommand(cmd)
            } else {
                SchedulerUtil.runTaskLater(command.delayTicks.toLong()) {
                    executeCommand(cmd)
                }
            }
        }

        ServerBoosterPlugin.debug("Executed command group '${group.name}' at TPS: $tps")
    }

    private fun executeCommand(command: String) {
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
        } catch (e: Exception) {
            plugin.logger.warning("Failed to execute command '$command': ${e.message}")
        }
    }

    fun forceExecute(groupName: String): Boolean {
        val group = config.commandGroups.find { it.name == groupName } ?: return false
        executeCommandGroup(group, plugin.getTps())
        return true
    }

    fun getGroups(): List<String> {
        return config.commandGroups.map { it.name }
    }
}
