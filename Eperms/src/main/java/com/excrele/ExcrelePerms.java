package com.excrele;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.ChatColor;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ExcrelePerms extends JavaPlugin implements Listener {
    private File configFile;
    private FileConfiguration ranksConfig;
    private Map<UUID, PermissionAttachment> playerPermissions;

    @Override
    public void onEnable() {
        // Register as event listener
        getServer().getPluginManager().registerEvents(this, this);

        // Initialize configuration
        configFile = new File(getDataFolder(), "ranks.yml");
        if (!configFile.exists()) {
            saveResource("ranks.yml", false);
        }
        ranksConfig = YamlConfiguration.loadConfiguration(configFile);
        playerPermissions = new HashMap<>();

        // Load permissions for online players
        getServer().getOnlinePlayers().forEach(this::loadPlayerPermissions);
        getLogger().info("ExcrelePerms enabled!");
    }

    @Override
    public void onDisable() {
        // Remove permissions for all players
        playerPermissions.values().forEach(PermissionAttachment::remove);
        playerPermissions.clear();
        getLogger().info("ExcrelePerms disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("rank")) {
            if (args.length == 0) {
                sender.sendMessage(ChatColor.RED + "Usage: /rank <add|assign|promote|demote|reload> [args]");
                return true;
            }

            switch (args[0].toLowerCase()) {
                case "add":
                    if (args.length < 2) {
                        sender.sendMessage(ChatColor.RED + "Usage: /rank add <player> <rank>");
                        return true;
                    }
                    return addPlayerToRank(sender, args[1], args[2]);
                case "assign":
                    if (args.length < 2) {
                        sender.sendMessage(ChatColor.RED + "Usage: /rank assign <player> <rank>");
                        return true;
                    }
                    return assignPlayerRank(sender, args[1], args[2]);
                case "promote":
                    if (args.length < 2) {
                        sender.sendMessage(ChatColor.RED + "Usage: /rank promote <player>");
                        return true;
                    }
                    return promotePlayer(sender, args[1]);
                case "demote":
                    if (args.length < 2) {
                        sender.sendMessage(ChatColor.RED + "Usage: /rank demote <player>");
                        return true;
                    }
                    return demotePlayer(sender, args[1]);
                case "reload":
                    reloadConfigFile(sender);
                    return true;
                default:
                    sender.sendMessage(ChatColor.RED + "Unknown subcommand!");
                    return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String rank = ranksConfig.getString("players." + player.getUniqueId() + ".rank", "default");
        String prefix = ranksConfig.getString("ranks." + rank + ".info.prefix", "");
        String suffix = ranksConfig.getString("ranks." + rank + ".info.suffix", "");

        // Translate color codes
        prefix = ChatColor.translateAlternateColorCodes('&', prefix);
        suffix = ChatColor.translateAlternateColorCodes('&', suffix);

        // Set custom join message with prefix and suffix
        String joinMessage = prefix + player.getName() + suffix + " joined the game";
        event.setJoinMessage(joinMessage);

        // Load permissions and display name
        loadPlayerPermissions(player);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String rank = ranksConfig.getString("players." + player.getUniqueId() + ".rank", "default");
        String prefix = ranksConfig.getString("ranks." + rank + ".info.prefix", "");
        String suffix = ranksConfig.getString("ranks." + rank + ".info.suffix", "");

        // Translate color codes
        prefix = ChatColor.translateAlternateColorCodes('&', prefix);
        suffix = ChatColor.translateAlternateColorCodes('&', suffix);

        // Build full name with prefix and suffix
        String fullName = prefix + player.getName() + suffix;

        // Set format: fullName + ": %s" (message placeholder)
        event.setFormat(fullName + ": %s");
    }

    private boolean addPlayerToRank(CommandSender sender, String playerName, String rank) {
        Player player = getServer().getPlayer(playerName);
        if (player == null) {
            sender.sendMessage(ChatColor.RED + "Player not found!");
            return true;
        }
        if (!ranksConfig.contains("ranks." + rank)) {
            sender.sendMessage(ChatColor.RED + "Rank " + rank + " does not exist!");
            return true;
        }
        String oldRank = ranksConfig.getString("players." + player.getUniqueId() + ".rank", "default");
        ranksConfig.set("players." + player.getUniqueId() + ".rank", rank);
        saveConfigFile();
        loadPlayerPermissions(player);
        // Fire RankAddEvent
        RankAddEvent event = new RankAddEvent(player, rank, oldRank, sender);
        getServer().getPluginManager().callEvent(event);
        sender.sendMessage(ChatColor.GREEN + "Assigned " + playerName + " to rank " + rank);
        return true;
    }

    private boolean assignPlayerRank(CommandSender sender, String playerName, String rank) {
        return addPlayerToRank(sender, playerName, rank); // Same logic as add
    }

    private boolean promotePlayer(CommandSender sender, String playerName) {
        Player player = getServer().getPlayer(playerName);
        if (player == null) {
            sender.sendMessage(ChatColor.RED + "Player not found!");
            return true;
        }
        String currentRank = ranksConfig.getString("players." + player.getUniqueId() + ".rank", "default");
        List<String> track = ranksConfig.getStringList("progression-tracks.defaultTrack");
        int currentIndex = track.indexOf(currentRank);
        if (currentIndex == -1 || currentIndex == track.size() - 1) {
            sender.sendMessage(ChatColor.RED + "Cannot promote " + playerName + " further!");
            return true;
        }
        String nextRank = track.get(currentIndex + 1);
        ranksConfig.set("players." + player.getUniqueId() + ".rank", nextRank);
        saveConfigFile();
        loadPlayerPermissions(player);
        // Fire RankPromoteEvent
        RankPromoteEvent event = new RankPromoteEvent(player, currentRank, nextRank, sender);
        getServer().getPluginManager().callEvent(event);
        sender.sendMessage(ChatColor.GREEN + "Promoted " + playerName + " to " + nextRank);
        return true;
    }

    private boolean demotePlayer(CommandSender sender, String playerName) {
        Player player = getServer().getPlayer(playerName);
        if (player == null) {
            sender.sendMessage(ChatColor.RED + "Player not found!");
            return true;
        }
        String currentRank = ranksConfig.getString("players." + player.getUniqueId() + ".rank", "default");
        List<String> track = ranksConfig.getStringList("progression-tracks.defaultTrack");
        int currentIndex = track.indexOf(currentRank);
        if (currentIndex <= 0) {
            sender.sendMessage(ChatColor.RED + "Cannot demote " + playerName + " further!");
            return true;
        }
        String previousRank = track.get(currentIndex - 1);
        ranksConfig.set("players." + player.getUniqueId() + ".rank", previousRank);
        saveConfigFile();
        loadPlayerPermissions(player);
        // Fire RankDemoteEvent
        RankDemoteEvent event = new RankDemoteEvent(player, currentRank, previousRank, sender);
        getServer().getPluginManager().callEvent(event);
        sender.sendMessage(ChatColor.GREEN + "Demoted " + playerName + " to " + previousRank);
        return true;
    }

    private void reloadConfigFile(CommandSender sender) {
        ranksConfig = YamlConfiguration.loadConfiguration(configFile);
        getServer().getOnlinePlayers().forEach(this::loadPlayerPermissions);
        // Fire RankReloadEvent
        RankReloadEvent event = new RankReloadEvent(sender);
        getServer().getPluginManager().callEvent(event);
        sender.sendMessage(ChatColor.GREEN + "Ranks configuration reloaded!");
    }

    private void loadPlayerPermissions(Player player) {
        // Remove existing permissions
        if (playerPermissions.containsKey(player.getUniqueId())) {
            playerPermissions.get(player.getUniqueId()).remove();
            playerPermissions.remove(player.getUniqueId());
        }

        // Load new permissions
        String rank = ranksConfig.getString("players." + player.getUniqueId() + ".rank", "default");
        PermissionAttachment attachment = player.addAttachment(this);
        playerPermissions.put(player.getUniqueId(), attachment);

        // Apply permissions
        List<String> permissions = ranksConfig.getStringList("ranks." + rank + ".permissions");
        for (String perm : permissions) {
            if (perm.startsWith("-")) {
                attachment.unsetPermission(perm.substring(1));
            } else {
                attachment.setPermission(perm, true);
            }
        }

        // Apply inherited permissions
        List<String> inheritances = ranksConfig.getStringList("ranks." + rank + ".inheritance");
        for (String inheritedRank : inheritances) {
            List<String> inheritedPerms = ranksConfig.getStringList("ranks." + inheritedRank + ".permissions");
            for (String perm : inheritedPerms) {
                if (perm.startsWith("-")) {
                    attachment.unsetPermission(perm.substring(1));
                } else {
                    attachment.setPermission(perm, true);
                }
            }
        }

        // Set prefix for display name (tab list and above head)
        String prefix = ranksConfig.getString("ranks." + rank + ".info.prefix", "");
        player.setDisplayName(ChatColor.translateAlternateColorCodes('&', prefix + player.getName()));
    }

    private void saveConfigFile() {
        try {
            ranksConfig.save(configFile);
        } catch (IOException e) {
            getLogger().severe("Could not save ranks.yml: " + e.getMessage());
        }
    }
}