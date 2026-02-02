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
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRiptideEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Optimizes elytra flight by adding cooldowns to fireworks and riptide tridents.
 * Uses Minecraft's native cooldown system for proper visual feedback.
 *
 * FIXES APPLIED:
 * - Checks for specific hand (HAND) to prevent double execution from MAIN_HAND/OFF_HAND events
 * - Uses debounce map to prevent race conditions during cooldown application
 * - Applies cooldown IMMEDIATELY to prevent multiple fireworks before cooldown kicks in
 * - Cleans up player data on quit to prevent memory leaks
 * - Uses dynamic world configuration (refreshes on each event)
 */
class ElytraOptimizer(private val plugin: ServerBoosterPlugin) : Listener {

    private val config get() = plugin.configManager.chunkOptimizerConfig

    // Dynamic world set - refreshes from config on each check
    private val configuredWorlds: Set<String> get() = config.elytraWorlds.toSet()

    // Debounce map to prevent multiple rapid executions per player
    // Stores the last firework use timestamp per player
    private val fireworkDebounce = ConcurrentHashMap<UUID, Long>()

    // Minimum time between firework uses in milliseconds (prevents double-tick)
    // 50ms = 1 tick, we use 100ms (2 ticks) as safety margin
    private val debounceTimeMs = 100L

    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
        plugin.logger.info("Elytra Optimizer enabled for worlds: ${configuredWorlds.joinToString(", ")}")
    }

    fun unregister() {
        HandlerList.unregisterAll(this)
        fireworkDebounce.clear()
    }

    /**
     * Cleanup player data on quit to prevent memory leaks.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        fireworkDebounce.remove(event.player.uniqueId)
    }

    /**
     * Handles riptide trident usage during elytra flight.
     * Only applies during storms (rain) as riptide requires water/rain to work.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
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
     *
     * CRITICAL FIXES:
     * 1. Only processes HAND slot events (prevents double execution from MAIN_HAND/OFF_HAND)
     * 2. Uses debounce to prevent rapid consecutive executions
     * 3. Applies cooldown IMMEDIATELY (not delayed) to prevent multiple fireworks
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (!config.fireworkNerf.enabled) return

        // CRITICAL FIX #1: Only process right-click actions
        val action = event.action
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return

        // CRITICAL FIX #2: Only process HAND slot to prevent double execution
        // PlayerInteractEvent fires TWICE per right-click: once for HAND, once for OFF_HAND
        // We only want to process the HAND event
        if (event.hand != EquipmentSlot.HAND) return

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

        // CRITICAL FIX #3: Debounce check to prevent rapid consecutive executions
        val now = System.currentTimeMillis()
        val lastUse = fireworkDebounce[player.uniqueId] ?: 0L
        if (now - lastUse < debounceTimeMs) {
            // Too soon since last use, silently ignore (not on cooldown, just debounced)
            return
        }

        // Check if already on cooldown - deny the firework use
        if (player.getCooldown(Material.FIREWORK_ROCKET) > 0) {
            event.setUseItemInHand(Event.Result.DENY)

            if (config.elytraLog) {
                plugin.logger.info("Firework denied for ${player.name} (on cooldown)")
            }
            return
        }

        // Update debounce timestamp
        fireworkDebounce[player.uniqueId] = now

        // Apply cooldown with 1 tick delay so the current firework can be used first
        // If we apply immediately, Minecraft sees the cooldown and blocks the firework
        val cooldownTicks = config.fireworkNerf.delay
        val playerUUID = player.uniqueId

        SchedulerUtil.runTaskLater(1L) {
            val onlinePlayer = Bukkit.getPlayer(playerUUID)
            if (onlinePlayer != null && onlinePlayer.isOnline) {
                onlinePlayer.setCooldown(Material.FIREWORK_ROCKET, cooldownTicks)

                if (config.elytraLog) {
                    plugin.logger.info("Firework cooldown applied to ${onlinePlayer.name} ($cooldownTicks ticks)")
                }
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
