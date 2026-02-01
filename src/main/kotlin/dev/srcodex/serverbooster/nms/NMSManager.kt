package dev.srcodex.serverbooster.nms

import dev.srcodex.serverbooster.ServerBoosterPlugin
import dev.srcodex.serverbooster.config.EntityOptimizerConfig
import dev.srcodex.serverbooster.util.MinecraftVersion
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import java.lang.reflect.Field
import java.lang.reflect.Method

class NMSManager(private val plugin: ServerBoosterPlugin) {

    private var initialized = false

    // Cached reflection objects
    private var minecraftServerClass: Class<*>? = null
    private var craftServerClass: Class<*>? = null
    private var craftWorldClass: Class<*>? = null
    private var craftEntityClass: Class<*>? = null
    private var entityClass: Class<*>? = null
    private var entityInsentientClass: Class<*>? = null

    // Cached methods
    private var getServerMethod: Method? = null
    private var getTpsMethod: Method? = null
    private var getHandleMethod: Method? = null
    private var getEntityHandleMethod: Method? = null
    private var setAwareMethod: Method? = null
    private var getAwareMethod: Method? = null

    // Cached fields
    private var activatedTickField: Field? = null
    private var awareField: Field? = null
    private var recentTpsField: Field? = null
    private var spigotConfigField: Field? = null

    fun initialize(): Boolean {
        return try {
            initializeReflection()
            initialized = true
            plugin.logger.info("NMS initialized for ${MinecraftVersion.currentVersion}")
            true
        } catch (e: Exception) {
            plugin.logger.warning("Failed to initialize NMS: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    private fun findCraftBukkitPackage(): String {
        // Paper 1.20.5+ uses unversioned CraftBukkit packages
        // Try unversioned first (modern Paper)
        try {
            Class.forName("org.bukkit.craftbukkit.CraftServer")
            return "org.bukkit.craftbukkit"
        } catch (e: ClassNotFoundException) {
            // Fall back to versioned package (older versions)
        }

        // Try versioned package
        return "org.bukkit.craftbukkit.${MinecraftVersion.nmsVersion}"
    }

    private fun initializeReflection() {
        val nmsPackage = if (MinecraftVersion.hasNewNmsPackageStructure) {
            "net.minecraft"
        } else {
            "net.minecraft.server.${MinecraftVersion.nmsVersion}"
        }

        // Paper 1.20.5+ uses unversioned CraftBukkit packages
        val obcPackage = findCraftBukkitPackage()

        // Initialize classes
        craftServerClass = Class.forName("$obcPackage.CraftServer")
        craftWorldClass = Class.forName("$obcPackage.CraftWorld")
        craftEntityClass = Class.forName("$obcPackage.entity.CraftEntity")

        minecraftServerClass = if (MinecraftVersion.hasNewNmsPackageStructure) {
            Class.forName("net.minecraft.server.MinecraftServer")
        } else {
            Class.forName("$nmsPackage.MinecraftServer")
        }

        entityClass = if (MinecraftVersion.hasNewNmsPackageStructure) {
            Class.forName("net.minecraft.world.entity.Entity")
        } else {
            Class.forName("$nmsPackage.Entity")
        }

        entityInsentientClass = if (MinecraftVersion.hasNewNmsPackageStructure) {
            Class.forName("net.minecraft.world.entity.EntityInsentient")
        } else {
            Class.forName("$nmsPackage.EntityInsentient")
        }

        // Get server method
        getServerMethod = minecraftServerClass?.getMethod("getServer")

        // Get TPS field/method
        initializeTpsAccess()

        // Get entity handle method
        getHandleMethod = craftWorldClass?.getMethod("getHandle")
        getEntityHandleMethod = craftEntityClass?.getMethod("getHandle")

        // Get activated tick field
        initializeActivatedTickField()

        // Get aware field for AI
        initializeAwareField()

        // Get spigot config field
        initializeSpigotConfigField()
    }

    private fun initializeTpsAccess() {
        try {
            // Try to get recentTps field
            recentTpsField = minecraftServerClass?.getDeclaredField("recentTps")
            recentTpsField?.isAccessible = true
        } catch (e: NoSuchFieldException) {
            // Try alternative methods for different versions
            try {
                getTpsMethod = minecraftServerClass?.getMethod("getRecentTps")
            } catch (e2: Exception) {
                plugin.logger.warning("Could not find TPS access method")
            }
        }
    }

    private fun initializeActivatedTickField() {
        try {
            // Field name varies between versions
            val fieldNames = listOf("activatedTick", "activeTick", "Y", "ae", "ag")
            for (name in fieldNames) {
                try {
                    activatedTickField = entityClass?.getDeclaredField(name)
                    activatedTickField?.isAccessible = true
                    break
                } catch (e: NoSuchFieldException) {
                    continue
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Could not find activatedTick field")
        }
    }

    private fun initializeAwareField() {
        try {
            // Try different field names based on version
            val fieldNames = listOf("aware", "bp", "bq", "bM", "bO")
            for (name in fieldNames) {
                try {
                    awareField = entityInsentientClass?.getDeclaredField(name)
                    awareField?.isAccessible = true
                    break
                } catch (e: NoSuchFieldException) {
                    continue
                }
            }

            // Try Paper's setAware method
            if (MinecraftVersion.isAtLeast(1, 14)) {
                try {
                    setAwareMethod = LivingEntity::class.java.getMethod("setAware", Boolean::class.javaPrimitiveType)
                    getAwareMethod = LivingEntity::class.java.getMethod("isAware")
                } catch (e: Exception) {
                    // Not Paper or older Paper version
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Could not find aware field/method")
        }
    }

    private fun initializeSpigotConfigField() {
        try {
            val worldServerClass = if (MinecraftVersion.hasNewNmsPackageStructure) {
                Class.forName("net.minecraft.server.level.WorldServer")
            } else {
                Class.forName("net.minecraft.server.${MinecraftVersion.nmsVersion}.WorldServer")
            }
            spigotConfigField = worldServerClass.getDeclaredField("spigotConfig")
            spigotConfigField?.isAccessible = true
        } catch (e: Exception) {
            // May not exist on all versions
        }
    }

    fun getTps(): Double {
        if (!initialized) return 20.0

        return try {
            val server = getServerMethod?.invoke(null)

            if (recentTpsField != null) {
                val tpsArray = recentTpsField!!.get(server) as DoubleArray
                tpsArray[0]
            } else if (getTpsMethod != null) {
                val tpsArray = getTpsMethod!!.invoke(server) as DoubleArray
                tpsArray[0]
            } else {
                // Fallback: Try Paper API
                try {
                    val tps = org.bukkit.Bukkit.getTPS()
                    tps[0]
                } catch (e: Exception) {
                    20.0
                }
            }
        } catch (e: Exception) {
            20.0
        }
    }

    fun getCurrentTick(): Int {
        if (!initialized) return 0

        return try {
            val server = getServerMethod?.invoke(null)
            val ticksField = minecraftServerClass?.getDeclaredField("currentTick")
            ticksField?.isAccessible = true
            ticksField?.getInt(server) ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun setEntityTicking(entity: Entity, ticking: Boolean) {
        if (!initialized) return

        try {
            val nmsEntity = getEntityHandleMethod?.invoke(entity) ?: return

            if (activatedTickField != null) {
                if (ticking) {
                    activatedTickField!!.setInt(nmsEntity, getCurrentTick())
                } else {
                    activatedTickField!!.setInt(nmsEntity, Int.MIN_VALUE)
                }
            }
        } catch (e: Exception) {
            ServerBoosterPlugin.debug("Failed to set entity ticking: ${e.message}")
        }
    }

    fun setEntityAI(entity: Entity, hasAI: Boolean) {
        if (!initialized) return
        if (entity !is LivingEntity) return

        try {
            // Try Paper API first
            if (setAwareMethod != null) {
                setAwareMethod!!.invoke(entity, hasAI)
                return
            }

            // Fall back to reflection
            val nmsEntity = getEntityHandleMethod?.invoke(entity) ?: return

            if (entityInsentientClass?.isInstance(nmsEntity) == true && awareField != null) {
                awareField!!.setBoolean(nmsEntity, hasAI)
            }
        } catch (e: Exception) {
            // Try Bukkit API as last resort
            try {
                entity.setAI(hasAI)
            } catch (e2: Exception) {
                ServerBoosterPlugin.debug("Failed to set entity AI: ${e.message}")
            }
        }
    }

    fun isEntityAware(entity: Entity): Boolean {
        if (!initialized) return true
        if (entity !is LivingEntity) return true

        return try {
            // Try Paper API first
            if (getAwareMethod != null) {
                return getAwareMethod!!.invoke(entity) as Boolean
            }

            // Fall back to reflection
            val nmsEntity = getEntityHandleMethod?.invoke(entity) ?: return true

            if (entityInsentientClass?.isInstance(nmsEntity) == true && awareField != null) {
                awareField!!.getBoolean(nmsEntity)
            } else {
                true
            }
        } catch (e: Exception) {
            true
        }
    }

    fun overrideWorldConfig(world: World, config: EntityOptimizerConfig) {
        if (!initialized || spigotConfigField == null) return

        try {
            val craftWorld = craftWorldClass?.cast(world)
            val worldServer = getHandleMethod?.invoke(craftWorld) ?: return
            val spigotConfig = spigotConfigField!!.get(worldServer) ?: return

            // Override mob spawn range
            setFieldValue(spigotConfig, "mobSpawnRange", config.mobSpawnRange.toByte())

            // Override entity activation ranges
            setFieldValue(spigotConfig, "tickInactiveVillagers", config.entityActivationRange.tickInactiveVillagers)
            setFieldValue(spigotConfig, "animalActivationRange", config.entityActivationRange.animals)
            setFieldValue(spigotConfig, "monsterActivationRange", config.entityActivationRange.monsters)
            setFieldValue(spigotConfig, "raiderActivationRange", config.entityActivationRange.raiders)
            setFieldValue(spigotConfig, "miscActivationRange", config.entityActivationRange.misc)

            // Override ticks per spawn
            world.setTicksPerAnimalSpawns(config.ticksPer.animalSpawns)
            world.setTicksPerMonsterSpawns(config.ticksPer.monsterSpawns)

        } catch (e: Exception) {
            plugin.logger.warning("Failed to override world config for ${world.name}: ${e.message}")
        }
    }

    private fun setFieldValue(obj: Any, fieldName: String, value: Any) {
        try {
            val field = obj.javaClass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(obj, value)
        } catch (e: Exception) {
            // Field may not exist in this version
        }
    }

    fun restoreAllEntities() {
        for (world in org.bukkit.Bukkit.getWorlds()) {
            if (!plugin.configManager.entityOptimizerConfig.worlds.contains(world.name)) continue

            for (entity in world.livingEntities) {
                try {
                    if (hasFrozenTag(entity)) {
                        setEntityAI(entity, true)
                        removeFrozenTag(entity)
                    }
                } catch (e: Exception) {
                    // Continue with other entities
                }
            }
        }
    }

    fun hasFrozenTag(entity: Entity): Boolean {
        if (!MinecraftVersion.hasPersistentDataContainer) return false

        return try {
            val pdc = entity.persistentDataContainer
            val key = org.bukkit.NamespacedKey(plugin, "serverbooster.frozen")
            pdc.has(key, org.bukkit.persistence.PersistentDataType.BYTE)
        } catch (e: Exception) {
            false
        }
    }

    fun setFrozenTag(entity: Entity) {
        if (!MinecraftVersion.hasPersistentDataContainer) return

        try {
            val pdc = entity.persistentDataContainer
            val key = org.bukkit.NamespacedKey(plugin, "serverbooster.frozen")
            pdc.set(key, org.bukkit.persistence.PersistentDataType.BYTE, 1)
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun removeFrozenTag(entity: Entity) {
        if (!MinecraftVersion.hasPersistentDataContainer) return

        try {
            val pdc = entity.persistentDataContainer
            val key = org.bukkit.NamespacedKey(plugin, "serverbooster.frozen")
            pdc.remove(key)
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun getTranslationKey(material: Material): String {
        return try {
            // Try to get the translation key via reflection (CraftMagicNumbers)
            val translationKey = getTranslationKeyViaReflection(material)
            if (translationKey != null) {
                return translationKey
            }

            // Fallback: construct the key manually
            // Format: block.minecraft.{name} or item.minecraft.{name}
            val key = material.key
            val name = key.key // e.g., "stone", "diamond_sword"

            if (material.isBlock) {
                "block.${key.namespace}.$name"
            } else {
                "item.${key.namespace}.$name"
            }
        } catch (e: Exception) {
            // Ultimate fallback
            val name = material.name.lowercase()
            if (material.isBlock) "block.minecraft.$name" else "item.minecraft.$name"
        }
    }

    private fun getTranslationKeyViaReflection(material: Material): String? {
        return try {
            val obcPackage = findCraftBukkitPackage()
            val craftMagicNumbersClass = Class.forName("$obcPackage.util.CraftMagicNumbers")

            if (material.isBlock) {
                // CraftMagicNumbers.getBlock(material).getDescriptionId()
                val getBlockMethod = craftMagicNumbersClass.getMethod("getBlock", Material::class.java)
                val block = getBlockMethod.invoke(null, material)

                // Try different method names for getting translation key
                val methodNames = listOf("getDescriptionId", "h", "a", "getName")
                for (methodName in methodNames) {
                    try {
                        val method = block.javaClass.getMethod(methodName)
                        val result = method.invoke(block)
                        if (result is String && result.startsWith("block.")) {
                            return result
                        }
                    } catch (e: NoSuchMethodException) {
                        continue
                    }
                }
            } else {
                // CraftMagicNumbers.getItem(material).getDescriptionId()
                val getItemMethod = craftMagicNumbersClass.getMethod("getItem", Material::class.java)
                val item = getItemMethod.invoke(null, material)

                if (item != null) {
                    val methodNames = listOf("getDescriptionId", "a", "getName")
                    for (methodName in methodNames) {
                        try {
                            val method = item.javaClass.getMethod(methodName)
                            val result = method.invoke(item)
                            if (result is String && result.startsWith("item.")) {
                                return result
                            }
                        } catch (e: NoSuchMethodException) {
                            continue
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun setCustomName(entity: Entity, component: Component) {
        try {
            // Try Paper API first
            if (MinecraftVersion.hasAdventureApi) {
                entity.customName(component)
                return
            }

            // Fall back to legacy
            val legacyName = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection()
                .serialize(component)
            @Suppress("DEPRECATION")
            entity.customName = legacyName
        } catch (e: Exception) {
            ServerBoosterPlugin.debug("Failed to set custom name: ${e.message}")
        }
    }
}
