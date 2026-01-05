package com.excrele.managers;

import com.excrele.ExcrelePerms;
import com.excrele.yaml.YAMLFileManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.Scanner;

/**
 * Manages bulk operations for ranks.
 */
@SuppressWarnings("deprecation")
public class BulkOperationsManager {
    private final ExcrelePerms plugin;
    private final YAMLFileManager fileManager;
    
    public BulkOperationsManager(ExcrelePerms plugin, YAMLFileManager fileManager) {
        this.plugin = plugin;
        this.fileManager = fileManager;
    }
    
    /**
     * Add rank to multiple players.
     */
    public BulkResult bulkAddRank(CommandSender sender, String rank, List<String> playerNames) {
        BulkResult result = new BulkResult();
        FileConfiguration ranksConfig = fileManager.getConfig("ranks.yml");
        
        if (!ranksConfig.contains("ranks." + rank)) {
            result.addError("Rank '" + rank + "' does not exist!");
            return result;
        }
        
        for (String playerName : playerNames) {
            try {
                UUID playerUUID = getPlayerUUID(playerName);
                if (playerUUID == null) {
                    result.addSkipped(playerName + " (not found)");
                    continue;
                }
                
                ranksConfig.set("players." + playerUUID + ".rank", rank);
                
                // Update online player
                Player onlinePlayer = plugin.getServer().getPlayer(playerUUID);
                if (onlinePlayer != null) {
                    plugin.loadPlayerPermissions(onlinePlayer);
                }
                
                result.addSuccess(playerName);
            } catch (Exception e) {
                result.addError(playerName + ": " + e.getMessage());
            }
        }
        
        fileManager.saveConfigSync("ranks.yml", ranksConfig);
        return result;
    }
    
    /**
     * Promote multiple players.
     */
    public BulkResult bulkPromote(CommandSender sender, List<String> playerNames, TrackManager trackManager) {
        BulkResult result = new BulkResult();
        FileConfiguration ranksConfig = fileManager.getConfig("ranks.yml");
        
        for (String playerName : playerNames) {
            try {
                UUID playerUUID = getPlayerUUID(playerName);
                if (playerUUID == null) {
                    result.addSkipped(playerName + " (not found)");
                    continue;
                }
                
                String currentRank = ranksConfig.getString("players." + playerUUID + ".rank", "default");
                String trackName = trackManager.getRankTrack(currentRank);
                if (trackName == null || trackName.isEmpty()) {
                    trackName = "defaultTrack";
                }
                
                String nextRank = trackManager.getNextRank(currentRank, trackName);
                if (nextRank == null) {
                    result.addSkipped(playerName + " (cannot promote further)");
                    continue;
                }
                
                ranksConfig.set("players." + playerUUID + ".rank", nextRank);
                
                Player onlinePlayer = plugin.getServer().getPlayer(playerUUID);
                if (onlinePlayer != null) {
                    plugin.loadPlayerPermissions(onlinePlayer);
                }
                
                result.addSuccess(playerName + " (" + currentRank + " → " + nextRank + ")");
            } catch (Exception e) {
                result.addError(playerName + ": " + e.getMessage());
            }
        }
        
        fileManager.saveConfigSync("ranks.yml", ranksConfig);
        return result;
    }
    
    /**
     * Demote multiple players.
     */
    public BulkResult bulkDemote(CommandSender sender, List<String> playerNames, TrackManager trackManager) {
        BulkResult result = new BulkResult();
        FileConfiguration ranksConfig = fileManager.getConfig("ranks.yml");
        
        for (String playerName : playerNames) {
            try {
                UUID playerUUID = getPlayerUUID(playerName);
                if (playerUUID == null) {
                    result.addSkipped(playerName + " (not found)");
                    continue;
                }
                
                String currentRank = ranksConfig.getString("players." + playerUUID + ".rank", "default");
                String trackName = trackManager.getRankTrack(currentRank);
                if (trackName == null || trackName.isEmpty()) {
                    trackName = "defaultTrack";
                }
                
                String previousRank = trackManager.getPreviousRank(currentRank, trackName);
                if (previousRank == null) {
                    result.addSkipped(playerName + " (cannot demote further)");
                    continue;
                }
                
                ranksConfig.set("players." + playerUUID + ".rank", previousRank);
                
                Player onlinePlayer = plugin.getServer().getPlayer(playerUUID);
                if (onlinePlayer != null) {
                    plugin.loadPlayerPermissions(onlinePlayer);
                }
                
                result.addSuccess(playerName + " (" + currentRank + " → " + previousRank + ")");
            } catch (Exception e) {
                result.addError(playerName + ": " + e.getMessage());
            }
        }
        
        fileManager.saveConfigSync("ranks.yml", ranksConfig);
        return result;
    }
    
    /**
     * Load player list from file.
     */
    public List<String> loadPlayerListFromFile(File file) {
        List<String> players = new ArrayList<>();
        try (Scanner scanner = new Scanner(file)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    players.add(line);
                }
            }
        } catch (FileNotFoundException e) {
            plugin.getLogger().warning("Player list file not found: " + file.getName());
        }
        return players;
    }
    
    /**
     * Get player UUID from name or UUID string.
     */
    private UUID getPlayerUUID(String playerName) {
        // Try as UUID first
        try {
            return UUID.fromString(playerName);
        } catch (IllegalArgumentException e) {
            // Not a UUID, try as player name
            Player onlinePlayer = plugin.getServer().getPlayer(playerName);
            if (onlinePlayer != null) {
                return onlinePlayer.getUniqueId();
            }
            
            OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(playerName);
            if (offlinePlayer.hasPlayedBefore()) {
                return offlinePlayer.getUniqueId();
            }
        }
        return null;
    }
    
    /**
     * Bulk operation result.
     */
    public static class BulkResult {
        private final List<String> successes = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private final List<String> skipped = new ArrayList<>();
        
        public void addSuccess(String message) {
            successes.add(message);
        }
        
        public void addError(String message) {
            errors.add(message);
        }
        
        public void addSkipped(String message) {
            skipped.add(message);
        }
        
        public List<String> getSuccesses() {
            return successes;
        }
        
        public List<String> getErrors() {
            return errors;
        }
        
        public List<String> getSkipped() {
            return skipped;
        }
        
        public int getTotal() {
            return successes.size() + errors.size() + skipped.size();
        }
    }
}

