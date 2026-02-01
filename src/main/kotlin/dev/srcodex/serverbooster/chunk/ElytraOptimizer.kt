package dev.srcodex.serverbooster.chunk

import dev.srcodex.serverbooster.ServerBoosterPlugin
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerRiptideEvent
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class ElytraOptimizer(private val plugin: ServerBoosterPlugin) : Listener {

    private val config get() = plugin.configManager.chunkOptimizerConfig

    // Cooldown tracking
    private val riptideCooldowns = ConcurrentHashMap<UUID, Long>()
    private val fireworkCooldowns = ConcurrentHashMap<UUID, Long>()

    private val configuredWorlds: Set<String> = config.elytraWorlds.toSet()

    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

    fun unregister() {
        HandlerList.unregisterAll(this)
        riptideCooldowns.clear()
        fireworkCooldowns.clear()
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onRiptide(event: PlayerRiptideEvent) {
        if (!config.riptideTridentNerf.enabled) return

        val player = event.player
        if (!isElytraFlying(player)) return
        if (!configuredWorlds.contains(player.world.name)) return

        val now = System.currentTimeMillis()
        val cooldownMs = config.riptideTridentNerf.delay * 50L // Convert ticks to ms
        val lastUse = riptideCooldowns[player.uniqueId] ?: 0L

        if (now - lastUse < cooldownMs) {
            // Cancel the boost effect by zeroing velocity
            player.velocity = player.velocity.multiply(0.1)

            if (config.elytraLog) {
                plugin.logger.info("Riptide nerf applied to ${player.name}")
            }
        }

        riptideCooldowns[player.uniqueId] = now
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (!config.fireworkNerf.enabled) return

        val player = event.player
        val item = event.item ?: return

        // Check if using firework while elytra flying
        if (item.type != Material.FIREWORK_ROCKET) return
        if (!isElytraFlying(player)) return
        if (!configuredWorlds.contains(player.world.name)) return

        val now = System.currentTimeMillis()
        val cooldownMs = config.fireworkNerf.delay * 50L
        val lastUse = fireworkCooldowns[player.uniqueId] ?: 0L

        if (now - lastUse < cooldownMs) {
            event.isCancelled = true

            if (config.elytraLog) {
                plugin.logger.info("Firework nerf applied to ${player.name}")
            }
            return
        }

        fireworkCooldowns[player.uniqueId] = now
    }

    private fun isElytraFlying(player: Player): Boolean {
        return player.isGliding && player.inventory.chestplate?.type == Material.ELYTRA
    }

    fun clearCooldowns() {
        riptideCooldowns.clear()
        fireworkCooldowns.clear()
    }
}
