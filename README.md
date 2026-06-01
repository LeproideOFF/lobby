# 🚀 Ultra-Optimized Minestom Spawn

An extreme-performance Minecraft spawn/lobby server built with [Minestom](https://github.com/Minestom/Minestom). This project is specifically designed to run on a tiny memory footprint (target: **48MB RAM**) while providing essential features for a modern spawn.

## ✨ Features

### 🛠 Nano WorldEdit
Built-in minimalist WorldEdit for basic building without heavy plugins:
- `//pos1` & `//pos2`: Define selection corners.
- `//set <block>`: Fill selection with a specific block.
- `/save`: Force world persistence to disk.

### 🏠 Spawn Mechanics
- **Anti-Void**: Automatic teleportation back to spawn if falling under Y=10.
- **Spawn Protection**: Block break/place protection within a 10-block radius.
- **Jump Pads**: Gold pressure plates launch players into the air with sounds and particles.
- **Hologram**: Floating "Welcome" text at the center.
- **Spawn Command**: `/spawn` to return to the center instantly.

### 📊 Live Monitoring & UX
- **Scoreboard**: Real-time Sidebar displaying player info and online count.
- **Tablist**: Customized Header/Footer with server branding.
- **Chat Formatting**: Clean and colored chat format (`Name > Message`).
- **Auto-Announcements**: Periodic tips and news in the chat.

## ⚡ Extreme Optimizations
- **Strict RAM Limits**: Configured with `-Xmx48M` using `SerialGC` for minimal overhead.
- **Minimalist Generation**: Void world with zero generation logic (starts with 1 dirt block).
- **Consolidated Code**: All logic is unified in a single class to reduce class-loading RAM usage.
- **Optimized Networking**: Reduced view distance (2 chunks) and tailored packet handling.

## 🛠 Installation & Usage

### Requirements
- **Java 21** or higher.
- **Maven**.

### Running the server
Clone the repo and run:
```bash
mvn clean compile exec:exec
```

### Persistence
The world is saved in the `/world` folder using the **Anvil** format. Chunks are automatically saved on shutdown or via the `/save` command.

---
*Developed for ForgiumMC - Dedicated to extreme performance.*
