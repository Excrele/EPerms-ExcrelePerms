# ExcrelePerms

ExcrelePerms (EPerms) is a Minecraft plugin for Spigot 1.21.5 that provides a simple yet powerful system for managing player ranks and permissions. Inspired by Essentials Group Manager, it allows server administrators to control ranks and permissions through in-game commands and a configurable `ranks.yml` file. The plugin supports rank categories (Player, Donation, Staff, Creator), permission inheritance, chat prefixes, and a progression track for promotions/demotions. It also includes an event-based API for integration with other plugins, such as `Ecore`, to log rank-related changes.

## Features

- **Rank Management**: Define ranks with permissions, inheritance, and chat prefixes in `ranks.yml`.
- **In-Game Commands**: Add, assign, promote, demote, or reload ranks using `/rank` commands.
- **Rank Categories**: Organized rank types (Player, Donation, Staff, Creator) with clear annotations.
- **Progression Tracks**: Define promotion/demotion order for seamless rank progression.
- **API Support**: Custom events (`RankAddEvent`, `RankPromoteEvent`, `RankDemoteEvent`, `RankReloadEvent`) for logging changes in other plugins.
- **Compatibility**: Works with EssentialsX for permission nodes and chat formatting.

## Installation

1. **Build the Plugin**:

   - Clone the repository or download the source files.
   - Ensure you have Maven installed.
   - Place the files in the following structure:

     ```
     src/main/java/com/excrele/
       - ExcrelePerms.java
       - RankAddEvent.java
       - RankPromoteEvent.java
       - RankDemoteEvent.java
       - RankReloadEvent.java
     src/main/resources/
       - plugin.yml
       - ranks.yml
     pom.xml
     ```
   - Run `mvn clean package` to build the plugin. The output `ExcrelePerms.jar` will be in the `target` directory.

2. **Install on Server**:

   - Stop your Minecraft server (Spigot/Paper 1.21.5).
   - Copy `ExcrelePerms.jar` to the `plugins` folder.
   - Start and stop the server to generate `plugins/ExcrelePerms/ranks.yml`.
   - Modify `ranks.yml` to customize ranks, permissions, and prefixes.

3. **Dependencies**:

   - Requires Spigot or Paper 1.21.5.
   - Optional: EssentialsX for compatibility with permission nodes (e.g., `essentials.help`) and chat formatting.

## Commands

All commands require the `excreleperms.use` permission (default: op).

- `/rank add <player> <rank>`: Assign a player to a specific rank.
- `/rank assign <player> <rank>`: Same as `/rank add`, for flexibility.
- `/rank promote <player>`: Promote a player to the next rank in the `defaultTrack`.
- `/rank demote <player>`: Demote a player to the previous rank in the `defaultTrack`.
- `/rank reload`: Reload the `ranks.yml` configuration file.

Example:

```
/rank add Steve vip
/rank promote Alex
/rank reload
```

## Configuration

The `ranks.yml` file (`plugins/ExcrelePerms/ranks.yml`) is the main configuration file. It is divided into sections for clarity:

- **Ranks**: Defines rank categories (Player, Donation, Staff, Creator) with permissions, inheritance, and chat prefixes.
  - `permissions`: List of permission nodes (e.g., `essentials.fly`). Use `-` to negate (e.g., `-excreleperms.mantogglesave`).
  - `inheritance`: List of ranks to inherit permissions from.
  - `info.prefix`: Chat prefix with color codes (e.g., `&6[VIP]&f `).
- **Progression Tracks**: Defines the order for promotions/demotions (e.g., `defaultTrack: [default, member, vip, elite]`).
- **Players**: Maps player UUIDs to their assigned ranks.

Example `ranks.yml` snippet:

```yaml
ranks:
  # --- Player Ranks ---
  default:
    default: true
    permissions:
      - essentials.help
      - essentials.spawn
    inheritance: []
    info:
      prefix: '&e'
      build: false
  member:
    permissions:
      - essentials.home
      - essentials.tpa
    inheritance:
      - default
    info:
      prefix: '&2[Member]&f '
      build: true
progression-tracks:
  defaultTrack:
    - default
    - member
    - vip
    - elite
```

## API for Developers

ExcrelePerms provides an event-based API for other plugins (e.g., `Ecore`) to listen for rank-related changes. The following custom events are available:

- `RankAddEvent`: Fired when a player is assigned a new rank (`/rank add` or `/rank assign`).
- `RankPromoteEvent`: Fired when a player is promoted (`/rank promote`).
- `RankDemoteEvent`: Fired when a player is demoted (`/rank demote`).
- `RankReloadEvent`: Fired when the configuration is reloaded (`/rank reload`).

### Example Listener (for Ecore)

```java
package com.example.ecore;

import com.excrele.RankAddEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class ExcrelePermsListener implements Listener {
    private final JavaPlugin plugin;

    public ExcrelePermsListener(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onRankAdd(RankAddEvent event) {
        plugin.getLogger().info(String.format(
            "[ExcrelePerms] Player %s (UUID: %s) had rank changed from %s to %s by %s",
            event.getPlayer().getName(), event.getPlayer().getUniqueId(),
            event.getOldRank(), event.getNewRank(), event.getSender().getName()
        ));
    }
}
```

To use the API:

1. Add `ExcrelePerms` as a soft dependency in your plugin's `plugin.yml`:

   ```yaml
   softdepend: [ExcrelePerms]
   ```
2. Register the listener in your plugin's main class:

   ```java
   new ExcrelePermsListener(this);
   ```
3. Implement logging logic (e.g., file, database) in the event handlers.

## Building from Source

1. Ensure Maven and Java 8+ are installed.
2. Clone the repository or set up the project structure as described above.
3. Run:

   ```bash
   mvn clean package
   ```
4. Find the compiled `ExcrelePerms.jar` in the `target` directory.

## Compatibility

- **Minecraft Version**: 1.21.5 (Spigot/Paper).
- **Dependencies**: Spigot API (provided by the server). EssentialsX recommended for full functionality.
- **Tested With**: Spigot 1.21.5, EssentialsX.

## License

This project is licensed under the MIT License. See the `LICENSE` file for details (not included in this repository yet).

## Contributing

Contributions are welcome! Please submit pull requests or open issues on the repository (if hosted) for bug reports or feature requests.

## Contact

For support, contact the developer through your preferred channel or open an issue on the repository.

---

*Last Updated: June 17, 2025/*