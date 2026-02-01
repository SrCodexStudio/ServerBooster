package dev.srcodex.serverbooster.util

import dev.srcodex.serverbooster.ServerBoosterPlugin
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.scheduler.BukkitTask
import java.util.function.Consumer

object SchedulerUtil {

    private val plugin: ServerBoosterPlugin
        get() = ServerBoosterPlugin.instance

    private val isFolia: Boolean
        get() = plugin.isFolia

    fun runTask(runnable: Runnable): Any {
        return if (isFolia) {
            runFoliaGlobal(runnable)
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable)
        }
    }

    fun runTaskLater(delay: Long, runnable: Runnable): Any {
        return if (isFolia) {
            runFoliaGlobalDelayed(runnable, delay)
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, runnable, delay)
        }
    }

    fun runTaskTimer(delay: Long, period: Long, runnable: Runnable): Any {
        return if (isFolia) {
            runFoliaGlobalTimer(runnable, delay, period)
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, runnable, delay, period)
        }
    }

    fun runAsync(runnable: Runnable): Any {
        return if (isFolia) {
            runFoliaAsync(runnable)
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable)
        }
    }

    fun runAsyncLater(delay: Long, runnable: Runnable): Any {
        return if (isFolia) {
            runFoliaAsyncDelayed(runnable, delay)
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, runnable, delay)
        }
    }

    fun runAsyncTimer(delay: Long, period: Long, runnable: Runnable): Any {
        return if (isFolia) {
            runFoliaAsyncTimer(runnable, delay, period)
        } else {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, delay, period)
        }
    }

    fun runForEntity(entity: Entity, runnable: Runnable): Any? {
        return if (isFolia) {
            runFoliaForEntity(entity, runnable)
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable)
        }
    }

    fun runForLocation(location: Location, runnable: Runnable): Any {
        return if (isFolia) {
            runFoliaForLocation(location, runnable)
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable)
        }
    }

    fun cancelTask(task: Any?) {
        when (task) {
            is BukkitTask -> task.cancel()
            else -> {
                try {
                    task?.javaClass?.getMethod("cancel")?.invoke(task)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    // Folia-specific methods using reflection
    @Suppress("UNCHECKED_CAST")
    private fun runFoliaGlobal(runnable: Runnable): Any {
        val scheduler = Bukkit::class.java.getMethod("getGlobalRegionScheduler").invoke(null)
        val consumerClass = Consumer::class.java
        val runMethod = scheduler.javaClass.getMethod("run", org.bukkit.plugin.Plugin::class.java, consumerClass)
        return runMethod.invoke(scheduler, plugin, Consumer<Any> { runnable.run() })
    }

    @Suppress("UNCHECKED_CAST")
    private fun runFoliaGlobalDelayed(runnable: Runnable, delay: Long): Any {
        val scheduler = Bukkit::class.java.getMethod("getGlobalRegionScheduler").invoke(null)
        val consumerClass = Consumer::class.java
        val runMethod = scheduler.javaClass.getMethod("runDelayed", org.bukkit.plugin.Plugin::class.java, consumerClass, Long::class.javaPrimitiveType)
        return runMethod.invoke(scheduler, plugin, Consumer<Any> { runnable.run() }, delay.coerceAtLeast(1))
    }

    @Suppress("UNCHECKED_CAST")
    private fun runFoliaGlobalTimer(runnable: Runnable, delay: Long, period: Long): Any {
        val scheduler = Bukkit::class.java.getMethod("getGlobalRegionScheduler").invoke(null)
        val consumerClass = Consumer::class.java
        val runMethod = scheduler.javaClass.getMethod("runAtFixedRate", org.bukkit.plugin.Plugin::class.java, consumerClass, Long::class.javaPrimitiveType, Long::class.javaPrimitiveType)
        return runMethod.invoke(scheduler, plugin, Consumer<Any> { runnable.run() }, delay.coerceAtLeast(1), period)
    }

    @Suppress("UNCHECKED_CAST")
    private fun runFoliaAsync(runnable: Runnable): Any {
        val scheduler = Bukkit::class.java.getMethod("getAsyncScheduler").invoke(null)
        val consumerClass = Consumer::class.java
        val runMethod = scheduler.javaClass.getMethod("runNow", org.bukkit.plugin.Plugin::class.java, consumerClass)
        return runMethod.invoke(scheduler, plugin, Consumer<Any> { runnable.run() })
    }

    @Suppress("UNCHECKED_CAST")
    private fun runFoliaAsyncDelayed(runnable: Runnable, delay: Long): Any {
        val scheduler = Bukkit::class.java.getMethod("getAsyncScheduler").invoke(null)
        val consumerClass = Consumer::class.java
        val timeUnit = java.util.concurrent.TimeUnit.MILLISECONDS
        val delayMs = delay * 50L
        val runMethod = scheduler.javaClass.getMethod("runDelayed", org.bukkit.plugin.Plugin::class.java, consumerClass, Long::class.javaPrimitiveType, java.util.concurrent.TimeUnit::class.java)
        return runMethod.invoke(scheduler, plugin, Consumer<Any> { runnable.run() }, delayMs.coerceAtLeast(1), timeUnit)
    }

    @Suppress("UNCHECKED_CAST")
    private fun runFoliaAsyncTimer(runnable: Runnable, delay: Long, period: Long): Any {
        val scheduler = Bukkit::class.java.getMethod("getAsyncScheduler").invoke(null)
        val consumerClass = Consumer::class.java
        val timeUnit = java.util.concurrent.TimeUnit.MILLISECONDS
        val delayMs = delay * 50L
        val periodMs = period * 50L
        val runMethod = scheduler.javaClass.getMethod("runAtFixedRate", org.bukkit.plugin.Plugin::class.java, consumerClass, Long::class.javaPrimitiveType, Long::class.javaPrimitiveType, java.util.concurrent.TimeUnit::class.java)
        return runMethod.invoke(scheduler, plugin, Consumer<Any> { runnable.run() }, delayMs.coerceAtLeast(1), periodMs, timeUnit)
    }

    @Suppress("UNCHECKED_CAST")
    private fun runFoliaForEntity(entity: Entity, runnable: Runnable): Any? {
        val scheduler = entity.javaClass.getMethod("getScheduler").invoke(entity)
        val consumerClass = Consumer::class.java
        val runMethod = scheduler.javaClass.getMethod("run", org.bukkit.plugin.Plugin::class.java, consumerClass, Runnable::class.java)
        return runMethod.invoke(scheduler, plugin, Consumer<Any> { runnable.run() }, null)
    }

    @Suppress("UNCHECKED_CAST")
    private fun runFoliaForLocation(location: Location, runnable: Runnable): Any {
        val scheduler = Bukkit::class.java.getMethod("getRegionScheduler").invoke(null)
        val consumerClass = Consumer::class.java
        val runMethod = scheduler.javaClass.getMethod("run", org.bukkit.plugin.Plugin::class.java, Location::class.java, consumerClass)
        return runMethod.invoke(scheduler, plugin, location, Consumer<Any> { runnable.run() })
    }
}
