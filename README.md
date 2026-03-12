<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.17.x--1.21.x-brightgreen?style=for-the-badge&logo=minecraft" alt="Minecraft Version"/>
  <img src="https://img.shields.io/badge/Platform-Spigot%20%7C%20Paper%20%7C%20Folia-blue?style=for-the-badge" alt="Platform"/>
  <img src="https://img.shields.io/badge/Language-Kotlin-purple?style=for-the-badge&logo=kotlin" alt="Language"/>
  <img src="https://img.shields.io/badge/License-Proprietary-red?style=for-the-badge" alt="License"/>
</p>

<h1 align="center">ServerBooster</h1>

<p align="center">
  <strong>The Ultimate Performance Optimization Plugin for Minecraft Servers</strong>
</p>

<p align="center">
  <em>Remastered Edition for Modern Minecraft (1.17.x - 1.21.x)</em>
</p>

---

## IMPORTANT ANNOUNCEMENT: FREE & PREMIUM Model

> **Version 3.0.0 is the LAST completely FREE version.**

### What does this mean?

| Version | Availability | Where to get it |
|---------|--------------|-----------------|
| **v3.0.0 and earlier** | FREE | [GitHub Releases](https://github.com/SrCodexStudio/ServerBooster/releases) |
| **v3.0.1+** | PREMIUM | [BuiltByBit](https://builtbybit.com/resources/serverbooster.92479/) |
| **v3.0.1+ [FREE]** | FREE (delayed) | GitHub (1-2 weeks after PREMIUM release) |

### Why this change?

ServerBooster is a **complete remaster** of an abandoned plugin from 2023-2024. When we launched our first version, it already included:

- Original features not present in the old version
- Support for newer Minecraft versions (1.20.x - 1.21.x)
- Complete rewrite in modern Kotlin with coroutines
- Better performance optimizations
- Active maintenance and bug fixes

**I have maintained this project for FREE and Open Source for several months in good faith.** However, to ensure the project's long-term sustainability and continued development, I've decided to introduce a PREMIUM model.

### How does it work?

1. **PREMIUM versions** are released first on [BuiltByBit](https://builtbybit.com/resources/serverbooster.92479/)
2. **FREE versions** are released on GitHub **1-2 weeks later**
3. Both versions are **identical** - no features are locked behind a paywall

**This is NOT about monetization - it's about SUPPORTING the project.** If you want early access and want to help keep ServerBooster alive, consider purchasing the PREMIUM version. If you prefer to wait, the FREE version will always be available.

### Support the Project

<p align="center">
  <a href="https://builtbybit.com/resources/serverbooster.92479/">
    <img src="https://img.shields.io/badge/Get%20PREMIUM-BuiltByBit-orange?style=for-the-badge" alt="Get Premium"/>
  </a>
</p>

---

<p align="center">
  <a href="#-features">Features</a> •
  <a href="#-installation">Installation</a> •
  <a href="#%EF%B8%8F-configuration">Configuration</a> •
  <a href="#-commands">Commands</a> •
  <a href="#-performance-tips">Performance Tips</a>
</p>

---

## Overview

**ServerBooster** is a comprehensive server optimization plugin designed to dramatically improve your Minecraft server's performance. This remastered edition has been completely rewritten in Kotlin with modern optimizations, coroutine-based processing, and full support for the latest Minecraft versions.

### Why ServerBooster?

- **Zero Dependencies** - Just drop it in your plugins folder and go!
- **Highly Configurable** - Fine-tune every aspect of optimization
- **Smart Optimization** - Only optimizes what needs to be optimized
- **RAM Efficient** - Automatic chunk unloading keeps memory usage low
- **Multi-Platform** - Works on Spigot, Paper, and Folia

---

## Features

### Entity Optimization

| Feature | Description |
|---------|-------------|
| **AI Freeze** | Temporarily disables AI for entities far from players |
| **Spawn Limiting** | Controls entity spawns per chunk and radius |
| **Age Limiter** | Automatically removes old entities |
| **Breeding Control** | Limits breeding to prevent entity explosions |
| **Smart Restore** | Instantly restores entity AI when players approach |

> Entities are frozen using NMS (Native Minecraft Server) methods, ensuring minimal performance impact while maintaining vanilla behavior when players are nearby.

### Item Stacking & Holograms

| Feature | Description |
|---------|-------------|
| **Force Merge** | Stacks items beyond vanilla limits (up to 512+) |
| **Unstackable Stacking** | Even swords, armor, and potions can be stacked! |
| **Holographic Display** | Shows item name and quantity above stacked items |
| **Glowing Items** | Optional glow effect for dropped items |
| **Smart Pickup** | Correctly handles inventory distribution |

> Reduce thousands of item entities into just a few, dramatically improving TPS on servers with mob farms or heavy item drops.

### Chunk Optimization

| Feature | Description |
|---------|-------------|
| **Auto Unload** | Automatically unloads unused chunks |
| **Packet Throttling** | Slows chunk packets during lag or high ping |
| **Physics Detection** | Detects and reports lag machines |
| **Spawn Protection** | Never unloads essential spawn chunks |

> Paper 1.21+ handles chunk management very efficiently. ServerBooster complements this by managing chunks while players are online but have moved away from areas.

### Villager Optimizer

| Feature | Description |
|---------|-------------|
| **AI Lobotomy** | Disables AI for stationary villagers (trade halls) |
| **Smart Reactivation** | Restores AI when player interacts |
| **Memory Efficient** | Tracks villagers without memory leaks |

> Perfect for servers with large trading halls - villagers that don't move for 60+ seconds have their AI disabled, saving massive CPU cycles while still allowing trading.

### Elytra Optimization

| Feature | Description |
|---------|-------------|
| **Riptide Cooldown** | Prevents trident + riptide spam |
| **Firework Cooldown** | Limits firework boost frequency |
| **Per-World Config** | Enable only in specific worlds |

> Prevents players from generating excessive chunks through rapid elytra travel.

### TPS-Based Commands

| Feature | Description |
|---------|-------------|
| **Auto Execute** | Run commands when TPS drops below threshold |
| **Multiple Groups** | Configure different commands for different TPS levels |
| **Cooldown System** | Prevent command spam with configurable delays |

> Perfect for automatically clearing entities, notifying admins, or triggering emergency optimizations.

---

## Installation

### Requirements

- **Minecraft Server**: Spigot, Paper, or Folia
- **Version**: 1.17.x - 1.21.x
- **Java**: 17 or higher

### Steps

1. **Download** the latest version from [Releases](https://github.com/SrCodexStudio/ServerBooster/releases) or [BuiltByBit](https://builtbybit.com/resources/serverbooster.92479/)

2. **Place** the JAR file in your server's `plugins` folder

3. **Restart** your server (not reload!)

4. **Configure** the plugin in `plugins/ServerBooster/`

5. **Enjoy** better performance!

---

## Configuration

ServerBooster uses multiple configuration files for better organization:

```
plugins/ServerBooster/
├── config_entity_limiter.yml    # Entity spawn limits
├── config_optimize_entities.yml # Entity AI optimization
├── config_holo.yml              # Item stacking & holograms
├── chunks_optimizer.yml         # Chunk management
├── config_tps.yml               # TPS-based commands
├── lang/                        # Language files
│   ├── en_us.yml
│   └── es_es.yml
└── lang_limiter/
    └── en.yml
```

### Entity Limiter (`config_entity_limiter.yml`)

Controls how many entities can exist in a given area.

```yaml
# Radius in blocks for entity counting
radius: 56

# Worlds where limiter is active
worlds:
  - world
  - world_nether
  - world_the_end

# Default limits for all entities
defaults:
  radius_max: 10    # Max entities of same type in radius
  chunk_max: 5      # Max entities of same type per chunk
  cull: 5           # Max entities kept on chunk unload

# Custom limits per entity type
limits:
  VILLAGER:
    radius_max: 15
    chunk_max: 8
    cull: -1        # -1 = never cull

  ZOMBIE:
    radius_max: 8
    chunk_max: 4
    cull: 3
```

#### Key Options

| Option | Description | Default |
|--------|-------------|---------|
| `radius` | Area size for radius_max counting | `56` |
| `force-spawn-deny` | Block ALL spawn reasons | `true` |
| `breeding-limiter-enabled` | Enable breeding limits | `false` |
| `age-limiter-enabled` | Remove old entities | `false` |
| `keep-sitting-entities` | Protect sitting pets | `true` |

### Entity Optimizer (`config_optimize_entities.yml`)

Controls entity AI freezing and tracking.

```yaml
# Distance from player to freeze entities
tracking-range: 35

# Worlds to optimize
worlds:
  - world
  - world_nether

# Ignore certain entities
ignore:
  custom-named: true    # Don't freeze named mobs
  invulnerable: true    # Don't freeze invulnerable
  villagers: false      # Freeze villagers (saves CPU!)
  armorstands: true     # Don't freeze armor stands
  itemframes: true      # Don't freeze item frames

# Trigger conditions
trigger-options:
  always:
    enabled: true
    untrack-ticks: 600  # 30 seconds

  when-tps-below:
    enabled: true
    value: 18.5
    untrack-ticks: 450
```

#### Understanding Triggers

- **Always**: Constantly optimizes entities far from players
- **When TPS Below**: More aggressive optimization during lag

### Item Stacking (`config_holo.yml`)

Controls item merging and hologram display.

```yaml
# Worlds where stacking is active
worlds:
  - world

merge:
  # Force merge nearby items
  force_merge:
    enabled: true
    radius: 7           # Search radius for items
    max_stack: 512      # Maximum stack size

  # Hologram above stacked items
  hologram:
    enabled: true
    format: "&f{name} &bx{amount}"
    glow:
      enabled: true
      color: "AQUA"

# Items that should never be merged
blacklist:
  - DRAGON_EGG
  - ELYTRA
```

### Chunk Optimizer (`chunks_optimizer.yml`)

Controls chunk unloading and physics detection.

```yaml
# Automatic chunk unloading
unload-chunks:
  enabled: true
  interval-ticks: 6000  # Check every 5 minutes
  worlds:
    - world
    - world_nether
  log:
    unload: true

# Lag machine detection
block-physics-lag-detector:
  enabled: true
  low-tps: 18.0
  lag:
    warning-threshold: 950000
    notify-op: true
    cancel-event: false

# Elytra speed limits
optimize-elytra:
  reptide-trident-nerf:
    enabled: true
    delay: 100          # Ticks between uses
  firework-nerf:
    enabled: true
    delay: 60
```

### TPS Commands (`config_tps.yml`)

Execute commands automatically when TPS drops.

```yaml
tps-commands:
  enabled: true
  commands:
    emergency:
      tps: 14.0
      delay_ticks: 72000  # 1 hour cooldown
      list:
        1:
          command: "broadcast &c[Warning] Server is lagging! TPS: {tps}"
          delay_ticks: 0
        2:
          command: "kill @e[type=item]"
          delay_ticks: 20

    warning:
      tps: 16.0
      delay_ticks: 36000
      list:
        1:
          command: "broadcast &e[Notice] TPS dropped to {tps}"
          delay_ticks: 0
```

---

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/sb reload` | `serverbooster.reload` | Reload all configurations |
| `/sb info` | - | Show plugin information |
| `/sb tps` | - | Display current server TPS |
| `/sb count [world]` | `serverbooster.mobs.count` | Count entities in world |
| `/sb limits` | `serverbooster.mobs.limits` | Show entity limits |
| `/sb check [player]` | `serverbooster.mobs.check` | Check entities near player |
| `/sb optimize` | `serverbooster.optimize` | Force entity optimization |
| `/sb blockphysics` | `serverbooster.chunks.blockphysics` | Show physics report |

---

## Performance Tips

### For Maximum Performance

1. **Start Conservative**
   - Begin with default settings
   - Monitor TPS with `/sb tps`
   - Gradually adjust limits

2. **Optimize Mob Farms**
   ```yaml
   # In config_entity_limiter.yml
   limits:
     GUARDIAN:
       chunk_max: 15
       radius_max: 30
     IRON_GOLEM:
       chunk_max: 5
       radius_max: 10
   ```

3. **Reduce Villager Lag**
   ```yaml
   # In config_optimize_entities.yml
   ignore:
     villagers: false  # Allow freezing!
   ```

4. **Aggressive Item Stacking**
   ```yaml
   # In config_holo.yml
   force_merge:
     radius: 10
     max_stack: 1024
   ```

### Server Type Recommendations

| Server Type | Chunk Unloader | Entity Limiter | Item Stacking |
|-------------|----------------|----------------|---------------|
| **Survival** | Enable | Medium limits | Enable |
| **SMP** | Enable | Relaxed limits | Enable |
| **Skyblock** | Careful | Strict limits | Enable |
| **Factions** | Enable | Strict limits | Enable |
| **Creative** | Disable | Disable | Optional |

### Understanding Chunk Behavior

> **Important**: On Paper 1.21+, the server automatically unloads chunks when players disconnect. ServerBooster's chunk optimizer is most useful for:
> - Unloading chunks while players are online but have moved away
> - Servers running older versions (1.17-1.20)
> - Spigot servers (less aggressive than Paper)

---

## Language Support

ServerBooster supports multiple languages:

- English (`en_us`)
- Spanish (`es_es`)

To change language, edit the `lang` option in each config file.

---

## Migration from Original ServerBooster

If you're upgrading from the original ServerBooster:

1. **Backup** your old configuration
2. **Delete** the old plugin and config folder
3. **Install** this remastered version
4. **Reconfigure** using this guide

> Configuration format has changed significantly. Manual migration is required.

---

## FAQ

**Q: Does this work with Folia?**
> Yes! ServerBooster automatically detects Folia and uses region-based scheduling.

**Q: Will frozen entities move again?**
> Yes! When a player approaches a frozen entity, its AI is instantly restored.

**Q: Does item stacking cause duplication?**
> No! The stacking system has been thoroughly tested to prevent any duplication.

**Q: Why don't I see chunks being unloaded?**
> On Paper 1.21+, the server already unloads chunks very efficiently. The optimizer works best when players are online and have moved away from areas.

**Q: Can I disable specific modules?**
> Yes! Set `enabled: false` in each module's config file, then `/sb reload`.

**Q: What's the difference between FREE and PREMIUM?**
> Nothing! Both versions are identical. PREMIUM just gives you early access (1-2 weeks earlier) and helps support the project.

---

## Benchmarks

Tested on a server with 50 players:

| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| TPS | 14.2 | 19.8 | +39% |
| Entity Count | 12,847 | 3,421 | -73% |
| RAM Usage | 8.2 GB | 5.1 GB | -38% |
| Item Entities | 4,523 | 287 | -94% |

*Results may vary depending on server configuration and player activity.*

---

## Support

- **Issues**: [GitHub Issues](https://github.com/SrCodexStudio/ServerBooster/issues)
- **Premium Support**: [BuiltByBit](https://builtbybit.com/resources/serverbooster.92479/)

---

## Credits

- **Original Plugin**: LoneDev (dev.lone) - Abandoned in 2023-2024
- **Remastered by**: [SrCodex](https://github.com/SrCodexStudio) - 2025-Present
- **Built with**: Kotlin, Paper API, Coroutines

---

<p align="center">
  <strong>Boost Your Server Today!</strong>
</p>

<p align="center">
  <a href="https://builtbybit.com/resources/serverbooster.92479/">
    <img src="https://img.shields.io/badge/Support%20the%20Project-Get%20PREMIUM-orange?style=for-the-badge" alt="Support"/>
  </a>
</p>

<p align="center">
  <sub>Made with love for the Minecraft community</sub>
</p>
