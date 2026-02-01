package dev.srcodex.serverbooster.entity

import dev.srcodex.serverbooster.ServerBoosterPlugin
import dev.srcodex.serverbooster.config.EntityLimit
import dev.srcodex.serverbooster.util.SchedulerUtil
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.entity.*
import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntityBreedEvent
import org.bukkit.event.world.ChunkUnloadEvent

class EntityLimiter(private val plugin: ServerBoosterPlugin) : Listener {

    private val config get() = plugin.configManager.entityLimiterConfig

    // Store radius at initialization (refreshed on reload when new instance is created)
    private val radiusChunks: Int = (plugin.configManager.entityLimiterConfig.radius / 16).coerceAtLeast(1)

    private var ageLimiterTask: Any? = null

    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)

        // Start age limiter if enabled
        if (config.ageLimiterEnabled) {
            runAgeLimiter()
        }
    }

    fun unregister() {
        HandlerList.unregisterAll(this)
        SchedulerUtil.cancelTask(ageLimiterTask)
    }

    // List of spawn reasons to limit (when forceSpawnDeny is false)
    private val limitedSpawnReasons = listOf(
        CreatureSpawnEvent.SpawnReason.BREEDING,
        CreatureSpawnEvent.SpawnReason.DEFAULT,
        CreatureSpawnEvent.SpawnReason.NATURAL,
        CreatureSpawnEvent.SpawnReason.SPAWNER,
        CreatureSpawnEvent.SpawnReason.SPAWNER_EGG,
        CreatureSpawnEvent.SpawnReason.NETHER_PORTAL,
        CreatureSpawnEvent.SpawnReason.CHUNK_GEN,
        CreatureSpawnEvent.SpawnReason.COMMAND, // /summon commands
        CreatureSpawnEvent.SpawnReason.CUSTOM,  // Plugin spawning
    )

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onCreatureSpawn(event: CreatureSpawnEvent) {
        val entity = event.entity
        val world = entity.world

        // Skip dropped items
        if (entity.type == EntityType.DROPPED_ITEM) return

        // Check if world is in config
        if (!config.worlds.contains(world.name)) return

        // Check spawn whitelist (by entity type name)
        if (config.spawnWhitelist.contains(entity.type.name)) return

        // Check spawn reason - if forceSpawnDeny is false, only limit certain reasons
        if (!config.forceSpawnDeny) {
            if (!limitedSpawnReasons.contains(event.spawnReason)) return
        }

        // Get limits for this entity type
        val limit = getEntityLimit(entity)
        val typeName = getEntityTypeName(entity)

        // Check radius limit first (like original)
        if (limit.radiusMax > 0) {
            val radiusCount = countEntitiesInRadiusByTypeName(entity, typeName)
            if (radiusCount >= limit.radiusMax) {
                handleSpawnDenied(event, entity, "radius_max", radiusCount, limit.radiusMax)
                return
            }
        }

        // Check chunk limit
        if (limit.chunkMax > 0) {
            val chunkCount = countEntitiesInChunkByTypeName(entity.location.chunk, typeName)
            if (chunkCount >= limit.chunkMax) {
                handleSpawnDenied(event, entity, "chunk_max", chunkCount, limit.chunkMax)
                return
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityBreed(event: EntityBreedEvent) {
        if (!config.breedingLimiterEnabled) return

        val entity = event.entity
        val world = entity.world

        if (!config.worlds.contains(world.name)) return

        val limit = getEntityLimit(entity)

        // Check chunk limit
        if (limit.chunkMax > 0) {
            val chunkCount = countEntitiesInChunk(entity.location.chunk, entity.type, entity)
            if (chunkCount >= limit.chunkMax) {
                event.isCancelled = true
                notifyBreedFailed(event.breeder, entity, limit)
                return
            }
        }

        // Check radius limit
        if (limit.radiusMax > 0) {
            val radiusCount = countEntitiesInRadius(entity)
            if (radiusCount >= limit.radiusMax) {
                event.isCancelled = true
                notifyBreedFailed(event.breeder, entity, limit)
                return
            }
        }

        // Apply breeding cooldown
        if (config.breedingTicks > 0) {
            val mother = event.mother
            val father = event.father

            if (mother is Animals) {
                mother.loveModeTicks = 0
                mother.setBreed(false)
            }
            if (father is Animals) {
                father.loveModeTicks = 0
                father.setBreed(false)
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onChunkUnload(event: ChunkUnloadEvent) {
        if (!config.worlds.contains(event.world.name)) return

        cullEntitiesInChunk(event.chunk)
    }

    private fun handleSpawnDenied(event: CreatureSpawnEvent, entity: LivingEntity, reason: String, count: Int, max: Int) {
        // Always cancel the event
        if (config.cancelEventInsteadOfRemove) {
            event.isCancelled = true
        } else {
            // Cancel AND remove (like original)
            event.isCancelled = true
            entity.remove()
        }

        if (config.debug) {
            plugin.logger.info("Denied spawn of ${entity.type} at ${entity.location} - $reason ($count/$max)")
        }
    }

    private fun notifyBreedFailed(breeder: LivingEntity?, entity: LivingEntity, limit: EntityLimit) {
        if (breeder is Player) {
            val message = plugin.languageManager.getLimiterMessage(
                "breed-failed-too-many-mobs",
                "mob_type" to entity.type.name.lowercase().replace("_", " "),
                "chunk_limit" to limit.chunkMax,
                "radius_limit" to limit.radiusMax,
                "radius_size" to config.radius,
                "blocks" to (config.radius + 16)
            )
            breeder.sendMessage(message)
        }
    }

    private fun getEntityLimit(entity: Entity): EntityLimit {
        val typeName = getEntityTypeName(entity)

        // Check for specific limit
        config.limits[typeName]?.let { return it }

        // Return defaults
        return EntityLimit(
            type = typeName,
            age = config.defaults.age,
            radiusMax = config.defaults.radiusMax,
            chunkMax = config.defaults.chunkMax,
            cull = config.defaults.cull
        )
    }

    private fun getEntityTypeName(entity: Entity): String {
        var name = entity.type.name

        // Check for sheep color
        if (config.sheepColorCheck && entity is Sheep) {
            name = "${name}_${entity.color?.name ?: "WHITE"}"
        }

        return name.uppercase()
    }

    private fun countEntitiesInChunk(chunk: Chunk, type: EntityType, exclude: Entity? = null): Int {
        return chunk.entities.count { entity ->
            entity.type == type && entity != exclude && !entity.isDead
        }
    }

    /**
     * Count entities in chunk by type name (allows for custom grouping like sheep colors)
     * This matches the original implementation which compares type name strings
     */
    private fun countEntitiesInChunkByTypeName(chunk: Chunk, typeName: String): Int {
        var count = 0
        for (entity in chunk.entities) {
            if (entity.isDead) continue
            if (getEntityTypeName(entity) == typeName) {
                count++
            }
        }
        return count
    }

    /**
     * Count entities in radius by type name
     * Uses getNearbyEntities for accurate distance calculation (like original)
     */
    private fun countEntitiesInRadiusByTypeName(sourceEntity: Entity, typeName: String): Int {
        var count = 0
        val location = sourceEntity.location
        val radius = config.radius.toDouble()

        // Use getNearbyEntities like the original code
        val nearbyEntities = location.world?.getNearbyEntities(location, radius, radius, radius) ?: return 0

        for (entity in nearbyEntities) {
            if (entity.isDead) continue
            if (entity is LivingEntity && getEntityTypeName(entity) == typeName) {
                count++
            }
        }
        return count
    }

    private fun countEntitiesInRadius(entity: Entity): Int {
        val typeName = getEntityTypeName(entity)
        val location = entity.location
        var count = 0

        // Check surrounding chunks
        val centerX = location.blockX shr 4
        val centerZ = location.blockZ shr 4

        for (x in (centerX - radiusChunks)..(centerX + radiusChunks)) {
            for (z in (centerZ - radiusChunks)..(centerZ + radiusChunks)) {
                if (!entity.world.isChunkLoaded(x, z)) continue

                val chunk = entity.world.getChunkAt(x, z)
                for (e in chunk.entities) {
                    if (e == entity || e.isDead) continue
                    if (getEntityTypeName(e) != typeName) continue

                    val distance = e.location.distanceSquared(location)
                    if (distance <= config.radius * config.radius) {
                        count++
                    }
                }
            }
        }

        return count
    }

    private fun cullEntitiesInChunk(chunk: Chunk) {
        val entityCounts = mutableMapOf<String, Int>()

        for (entity in chunk.entities) {
            if (entity.isDead) continue
            if (!isLimitableEntity(entity)) continue
            if (isExemptFromCull(entity)) continue

            val limit = getEntityLimit(entity)
            if (limit.cull < 0) continue

            val typeName = getEntityTypeName(entity)
            val count = entityCounts.getOrDefault(typeName, 0) + 1
            entityCounts[typeName] = count

            if (count > limit.cull) {
                entity.remove()
                if (config.debug) {
                    plugin.logger.info("Chunk unload culled: ${entity.type} at ${entity.location}")
                }
            }
        }
    }

    private fun isLimitableEntity(entity: Entity): Boolean {
        return entity is LivingEntity &&
                entity !is Player &&
                entity !is ArmorStand
    }

    private fun isExemptFromCull(entity: Entity): Boolean {
        // Check whitelist
        if (config.spawnWhitelist.contains(entity.type.name)) return true

        // Check for villagers (special protection)
        if (entity is Villager) return true

        // Check for custom name
        if (entity.customName() != null) return true

        // Check for tamed animals
        if (entity is Tameable && entity.isTamed) return true

        // Check for sitting entities
        if (config.keepSittingEntities && entity is Sittable && entity.isSitting) return true

        // Check for leashed entities
        if (entity is LivingEntity && entity.isLeashed) return true

        return false
    }

    // Age limiter task
    private fun runAgeLimiter() {
        ageLimiterTask = SchedulerUtil.runTaskTimer(600L, 600L) {
            var removed = 0

            for (world in Bukkit.getWorlds()) {
                if (!config.worlds.contains(world.name)) continue

                for (entity in world.livingEntities.toList()) {
                    if (entity.isDead) continue
                    if (!isLimitableEntity(entity)) continue
                    if (isExemptFromCull(entity)) continue

                    val limit = getEntityLimit(entity)
                    if (limit.age <= 0) continue

                    if (entity.ticksLived >= limit.age) {
                        if (entity is Damageable) {
                            entity.damage(1000.0)
                        } else {
                            entity.remove()
                        }
                        removed++

                        if (config.debug) {
                            plugin.logger.info("Age limit removed: ${entity.type} at ${entity.location}")
                        }
                    }
                }
            }

            if (removed > 0 && config.debug) {
                plugin.logger.info("Age limit sweep removed $removed entities")
            }
        }
    }

    fun getEntityCountInChunk(chunk: Chunk): Map<EntityType, Int> {
        val counts = mutableMapOf<EntityType, Int>()
        for (entity in chunk.entities) {
            if (entity !is LivingEntity) continue
            counts[entity.type] = counts.getOrDefault(entity.type, 0) + 1
        }
        return counts
    }

    fun getEntityCountInRadius(center: org.bukkit.Location, radius: Int): Map<EntityType, Int> {
        val counts = mutableMapOf<EntityType, Int>()
        val entities = center.world?.getNearbyEntities(center, radius.toDouble(), radius.toDouble(), radius.toDouble())
            ?: return counts

        for (entity in entities) {
            if (entity !is LivingEntity) continue
            counts[entity.type] = counts.getOrDefault(entity.type, 0) + 1
        }
        return counts
    }
}
