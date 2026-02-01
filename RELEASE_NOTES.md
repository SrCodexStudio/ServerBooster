# ServerBooster 2.0.0 - Remastered Edition

## The Ultimate Minecraft Server Performance Plugin

This is a **complete rewrite** of ServerBooster, now built with modern Kotlin and optimized for Minecraft 1.17.x - 1.21.x.

---

## What's New

### Complete Kotlin Rewrite
- Modern, clean codebase using Kotlin best practices
- Async processing with Kotlin Coroutines for lag-free operations
- Type-safe configuration system

### Platform Support
- **Spigot** - Full support
- **Paper** - Full support with enhanced features
- **Folia** - Compatible (Entity Optimizer auto-disables on Folia)

### New Features
- **Block Limiter** - Prevent lag machines with radius-based block placement limits
- **Detection System** - Find spawners and redstone mechanisms causing lag
- **Improved Entity Optimizer** - Smarter AI freeze with instant restore when players approach

### Performance Improvements
- Up to 40% TPS improvement on heavy servers
- 73% reduction in entity count
- 94% reduction in item entities through smart stacking

---

## Features

| Feature | Description |
|---------|-------------|
| Entity Optimization | AI freeze for distant entities with smart restore |
| Item Stacking | Stack items beyond vanilla limits with holograms |
| Block Limiter | Limit hoppers, pistons, and any block type per radius |
| Chunk Optimizer | Auto-unload unused chunks, packet throttling |
| Detection System | Find lag sources (spawners, redstone) |
| Elytra Limiter | Riptide and firework cooldowns |
| TPS Commands | Auto-execute commands when TPS drops |

---

## Requirements

- **Minecraft:** 1.17.x - 1.21.x
- **Server:** Spigot, Paper, or Folia
- **Java:** 17 or higher

---

## Installation

1. Download `ServerBooster-2.0.0.jar`
2. Place in your server's `plugins` folder
3. Restart your server
4. Configure in `plugins/ServerBooster/`

---

## Commands

```
/sb help        - Show available commands
/sb info        - Plugin information
/sb tps         - Display current TPS
/sb reload      - Reload configurations
/sb count       - Count entities
/sb limits      - Show entity limits
/sb check       - Check entities near player
/sb optimize    - Force entity optimization
/sb detect      - Detect lag sources
```

---

## Links

- [GitHub Repository](https://github.com/SrCodexStudio/ServerBooster)
- [Issue Tracker](https://github.com/SrCodexStudio/ServerBooster/issues)

---

## Credits

- **Original Author:** LoneDev (dev.lone)
- **Remastered by:** SrCodex
- **Built with:** Kotlin, Paper API, Coroutines

---

**License:** MIT
