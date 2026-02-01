package dev.srcodex.serverbooster.util

import org.bukkit.Bukkit

object MinecraftVersion {

    val currentVersion: String by lazy {
        Bukkit.getBukkitVersion().split("-")[0]
    }

    val major: Int by lazy {
        currentVersion.split(".").getOrNull(0)?.toIntOrNull() ?: 1
    }

    val minor: Int by lazy {
        currentVersion.split(".").getOrNull(1)?.toIntOrNull() ?: 0
    }

    val patch: Int by lazy {
        currentVersion.split(".").getOrNull(2)?.toIntOrNull() ?: 0
    }

    val nmsVersion: String by lazy {
        // For 1.17+ the package structure changed
        "v${major}_${minor}_R1"
    }

    val isSupported: Boolean by lazy {
        isAtLeast(1, 17) && !isHigherThan(1, 21, 99)
    }

    // Version checks
    val is1_17: Boolean by lazy { minor == 17 }
    val is1_18: Boolean by lazy { minor == 18 }
    val is1_19: Boolean by lazy { minor == 19 }
    val is1_20: Boolean by lazy { minor == 20 }
    val is1_21: Boolean by lazy { minor == 21 }

    // Feature availability checks
    val hasPersistentDataContainer: Boolean = true // Available since 1.14
    val hasAdventureApi: Boolean by lazy { isAtLeast(1, 16, 5) }
    val hasNewNmsPackageStructure: Boolean by lazy { isAtLeast(1, 17) } // 1.17+ uses net.minecraft instead of net.minecraft.server.VERSION

    fun isAtLeast(major: Int, minor: Int, patch: Int = 0): Boolean {
        if (this.major > major) return true
        if (this.major < major) return false
        if (this.minor > minor) return true
        if (this.minor < minor) return false
        return this.patch >= patch
    }

    fun isHigherThan(major: Int, minor: Int, patch: Int = 0): Boolean {
        if (this.major > major) return true
        if (this.major < major) return false
        if (this.minor > minor) return true
        if (this.minor < minor) return false
        return this.patch > patch
    }

    fun isExactly(major: Int, minor: Int, patch: Int = 0): Boolean {
        return this.major == major && this.minor == minor && this.patch == patch
    }
}
