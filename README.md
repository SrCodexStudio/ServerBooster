<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.17.x--1.21.x-brightgreen?style=for-the-badge&logo=minecraft" alt="Minecraft Version"/>
  <img src="https://img.shields.io/badge/Platform-Spigot%20%7C%20Paper%20%7C%20Folia-blue?style=for-the-badge" alt="Platform"/>
  <img src="https://img.shields.io/badge/Language-Kotlin-purple?style=for-the-badge&logo=kotlin" alt="Language"/>
  <img src="https://img.shields.io/badge/License-MIT-green?style=for-the-badge" alt="License"/>
</p>

<h1 align="center">ServerBooster</h1>

<p align="center">
  <strong>The Ultimate Performance Optimization Plugin for Minecraft Servers</strong>
</p>

<p align="center">
  <em>Remastered Edition for Modern Minecraft (1.17.x - 1.21.x)</em>
</p>

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
- **Async Processing** - Uses Kotlin Coroutines for lag-free operations

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

### Block Limiter (NEW)

| Feature | Description |
|---------|-------------|
| **Radius-Based Detection** | Limits blocks within a configurable radius (like entity limiter) |
| **Predefined Categories** | Groups like "hoppers", "pistons", "furnaces" |
| **Custom Materials** | Add ANY block by its Material name |
| **Global Limits** | Limit building blocks (stone, wood, etc.) |
| **Entity Support** | Limits armor stands, item frames, paintings |
| **Non-Destructive** | Existing blocks are never removed |

> Prevent lag machines and excessive block placement. Fully customizable per-block limits with radius-based detection.

### Chunk Optimization

| Feature | Description |
|---------|-------------|
| **Auto Unload** | Automatically unloads unused chunks |
| **Packet Throttling** | Slows chunk packets during lag or high ping |
| **Physics Detection** | Detects and reports lag machines |
| **Spawn Protection** | Never unloads essential spawn chunks |

> Paper 1.21+ handles chunk management very efficiently. ServerBooster complements this by managing chunks while players are online but have moved away from areas.

### Detection System (NEW)

| Feature | Description |
|---------|-------------|
| **Spawner Detection** | Find all spawners near online players |
| **Redstone Detection** | Locate high-density redstone mechanisms |
| **Async Processing** | Uses coroutines for lag-free scanning |
| **Teleport Commands** | Quick navigation to detected locations |
| **Player Proximity** | Shows which players are near each detection |

> Powerful admin tool to identify and locate potential lag sources across your server.

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

1. **Download** the latest `ServerBooster-2.0.0.jar` from [Releases](https://github.com/SrCodexStudio/ServerBooster/releases)

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
├── chunk_block_limits.yml       # Block placement limits (NEW)
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

### Block Limiter (`chunk_block_limits.yml`) - NEW

Controls how many blocks of each type can be placed within a radius.

```yaml
enabled: true

# Radius in blocks (same system as entity limiter)
radius: 56

# Worlds where limiter is active
worlds:
  - world
  - world_nether
  - world_the_end

# Message when placement is denied
deny-message: "&cYou cannot place more {block} in this area! &7(Limit: {limit})"

# Bypass permission
bypass-permission: "serverbooster.blocklimits.bypass"

# Block limits within radius
limits:
  # === Predefined Categories ===
  armor-stands: 4
  item-frames: 4
  glow-item-frames: 4
  paintings: 4

  # Storage
  chests: 4
  shulker-boxes: 8
  barrels: 4
  ender-chests: 2

  # Redstone (lag sources)
  hoppers: 8
  pistons: 8
  observers: 8
  dispensers: 4
  droppers: 4
  comparators: 8
  repeaters: 16

  # Workstations
  crafting-tables: 4
  furnaces: 4
  anvils: 2
  brewing-stands: 4

  # === Custom Materials ===
  # Add ANY block using its Material name!
  TNT: 4
  SPAWNER: 2
  JUKEBOX: 4
  NOTE_BLOCK: 8
  BELL: 2

# Global limits for building blocks
global-limits:
  enabled: true
  default-limit: 120    # Default for unlisted blocks

  limits:
    stone-variants: 256   # Stone, cobblestone, granite, etc.
    wood-variants: 256    # All wood types
    terracotta: 128       # All terracotta colors
    concrete: 128         # All concrete colors
    glass: 128            # All glass types
    wool: 128             # All wool colors
```

#### Adding Custom Blocks

You can limit ANY block by using its exact Material name:

```yaml
limits:
  # Predefined categories
  hoppers: 8
  pistons: 8

  # Custom blocks - use exact Material names (UPPERCASE)
  TNT: 4
  SPAWNER: 2
  JUKEBOX: 4
  NOTE_BLOCK: 8
  BEACON: 1
  CONDUIT: 1
  LODESTONE: 2
  RESPAWN_ANCHOR: 2
  CAMPFIRE: 4
  SOUL_CAMPFIRE: 4
```

> **Material Names**: Use exact Bukkit material names in UPPERCASE with underscores.
> Full list: https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Material.html

#### Important Notes

- **Non-Destructive**: Existing blocks that exceed limits are NEVER removed
- **Placement Only**: Limits only apply when placing NEW blocks
- **Bypass Permission**: `serverbooster.blocklimits.bypass`

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
| `/sb info` | - | Show plugin information and modules |
| `/sb tps` | - | Display current server TPS |
| `/sb count [world]` | `serverbooster.mobs.count` | Count entities in world |
| `/sb limits` | `serverbooster.mobs.limits` | Show entity limits |
| `/sb check [player]` | `serverbooster.mobs.check` | Check entities near player |
| `/sb optimize` | `serverbooster.optimize` | Force entity optimization |
| `/sb blockphysics` | `serverbooster.chunks.blockphysics` | Show physics report |
| `/sb detect spawners [type]` | `serverbooster.detect` | Detect spawners near players |
| `/sb detect redstone` | `serverbooster.detect` | Detect redstone mechanisms |

### Detection Commands

The detection system uses async coroutines to scan loaded chunks near online players:

**Spawner Detection** (`/sb detect spawners [entity_type]`)
```
/sb detect spawners          # Find all spawners
/sb detect spawners zombie   # Find only zombie spawners
/sb detect spawners skeleton # Find only skeleton spawners
```
- Scans within 50 blocks of online players
- Shows spawner type, location, and nearby entities
- Indicates if spawner is ACTIVE (player within 16 blocks)
- Provides teleport commands for easy navigation

**Redstone Detection** (`/sb detect redstone`)
- Finds high-activity components (observers, pistons, hoppers)
- Shows density of redstone components nearby
- Color-coded: Green (low), Yellow (medium), Red (high)
- Helps identify potential lag machines

---

## Permissions

| Permission | Description |
|------------|-------------|
| `serverbooster.reload` | Reload plugin configuration |
| `serverbooster.mobs.count` | Use `/sb count` command |
| `serverbooster.mobs.limits` | Use `/sb limits` command |
| `serverbooster.mobs.check` | Use `/sb check` command |
| `serverbooster.optimize` | Use `/sb optimize` command |
| `serverbooster.chunks.blockphysics` | Use `/sb blockphysics` command |
| `serverbooster.detect` | Use `/sb detect` commands |
| `serverbooster.blocklimits.bypass` | Bypass block placement limits |

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

5. **Limit Redstone Machines**
   ```yaml
   # In chunk_block_limits.yml
   limits:
     hoppers: 8
     pistons: 8
     observers: 8
     comparators: 8
   ```

### Server Type Recommendations

| Server Type | Chunk Unloader | Entity Limiter | Block Limiter | Item Stacking |
|-------------|----------------|----------------|---------------|---------------|
| **Survival** | Enable | Medium | Medium | Enable |
| **SMP** | Enable | Relaxed | Relaxed | Enable |
| **Skyblock** | Careful | Strict | Strict | Enable |
| **Factions** | Enable | Strict | Strict | Enable |
| **Creative** | Disable | Disable | Disable | Optional |

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

**Q: What happens to existing blocks that exceed limits?**
> Nothing! Existing blocks are NEVER removed. Limits only apply to NEW placements.

**Q: Why don't I see chunks being unloaded?**
> On Paper 1.21+, the server already unloads chunks very efficiently. The optimizer works best when players are online and have moved away from areas.

**Q: Can I disable specific modules?**
> Yes! Set `enabled: false` in each module's config file, then `/sb reload`.

**Q: How do I add a custom block to the limiter?**
> Add the Material name directly to the `limits` section in `chunk_block_limits.yml`:
> ```yaml
> limits:
>   TNT: 4
>   SPAWNER: 2
> ```

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
- **Discord**: Coming soon

---

## Credits

- **Original Author**: LoneDev (dev.lone)
- **Remastered by**: [SrCodex](https://github.com/SrCodexStudio)
- **Built with**: Kotlin, Paper API, Coroutines

---

<p align="center">
  <strong>Boost Your Server Today!</strong>
</p>

<p align="center">
  <sub>Made with care for the Minecraft community</sub>
</p>
