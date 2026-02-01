package dev.srcodex.serverbooster.chunk

import dev.srcodex.serverbooster.ServerBoosterPlugin
import dev.srcodex.serverbooster.util.SchedulerUtil
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerRiptideEvent
import org.bukkit.inventory.ItemStack

/**
 * Optimizes elytra flight by adding cooldowns to fireworks and riptide tridents.
 * Uses Minecraft's native cooldown system for proper visual feedback.
 */
class ElytraOptimizer(private val plugin: ServerBoosterPlugin) : Listener {

    private val config get() = plugin.configManager.chunkOptimizerConfig
    private val configuredWorlds: Set<String> = config.elytraWorlds.toSet()

    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
        plugin.logger.info("Elytra Optimizer enabled for worlds: ${configuredWorlds.joinToString(", ")}")
    }

    fun unregister() {
        HandlerList.unregisterAll(this)
    }

    /**
     * Handles riptide trident usage during elytra flight.
     * Only applies during storms (rain) as riptide requires water/rain to work.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onRiptide(event: PlayerRiptideEvent) {
        if (!config.riptideTridentNerf.enabled) return

        val player = event.player

        // Riptide only works during storms
        if (!player.world.hasStorm()) return

        // Must be gliding with elytra
        if (!player.isGliding) return
        if (!configuredWorlds.contains(player.world.name)) return

        // Check if wearing elytra and holding riptide trident
        val inventory = player.inventory
        if (inventory.chestplate?.type != Material.ELYTRA) return

        val mainHand = inventory.itemInMainHand
        val offHand = inventory.itemInOffHand

        if (!hasRiptideEnchant(mainHand) && !hasRiptideEnchant(offHand)) return

        // Check if already on cooldown
        if (player.getCooldown(Material.TRIDENT) > 0) {
            return
        }

        // Set the cooldown using Minecraft's native system
        player.setCooldown(Material.TRIDENT, config.riptideTridentNerf.delay)

        if (config.elytraLog) {
            plugin.logger.info("Riptide cooldown applied to ${player.name} (${config.riptideTridentNerf.delay} ticks)")
        }
    }

    /**
     * Handles firework rocket usage during elytra flight.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (!config.fireworkNerf.enabled) return
        if (!event.hasItem()) return

        val player = event.player
        val item = event.item ?: return

        // Check if using firework while elytra flying
        if (item.type != Material.FIREWORK_ROCKET) return
        if (!player.isGliding) return
        if (!configuredWorlds.contains(player.world.name)) return

        // Check if wearing elytra
        val chestplate = player.inventory.chestplate
        if (chestplate == null || chestplate.type != Material.ELYTRA) return

        // Check if already on cooldown - deny the firework use
        if (player.getCooldown(Material.FIREWORK_ROCKET) > 0) {
            event.setUseItemInHand(Event.Result.DENY)

            if (config.elytraLog) {
                plugin.logger.info("Firework denied for ${player.name} (on cooldown)")
            }
            return
        }

        // Set cooldown after a small delay (5 ticks) to allow the current firework to be used
        SchedulerUtil.runTaskLater(5L) {
            player.setCooldown(Material.FIREWORK_ROCKET, config.fireworkNerf.delay)

            if (config.elytraLog) {
                plugin.logger.info("Firework cooldown applied to ${player.name} (${config.fireworkNerf.delay} ticks)")
            }
        }
    }

    /**
     * Checks if an item is a trident with the Riptide enchantment.
     */
    private fun hasRiptideEnchant(item: ItemStack?): Boolean {
        if (item == null) return false
        if (item.type != Material.TRIDENT) return false
        if (!item.hasItemMeta()) return false

        val meta = item.itemMeta ?: return false
        return meta.hasEnchant(Enchantment.RIPTIDE)
    }
}
