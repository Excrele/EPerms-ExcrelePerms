package com.excrele.yaml;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Manages rank change history in YAML format.
 */
public class YAMLHistoryManager {
    @SuppressWarnings("unused")
    private final JavaPlugin plugin;
    private final YAMLFileManager fileManager;
    private final int maxHistoryPerPlayer;
    
    public YAMLHistoryManager(JavaPlugin plugin, YAMLFileManager fileManager) {
        this.plugin = plugin;
        this.fileManager = fileManager;
        this.maxHistoryPerPlayer = fileManager.getConfig("config.yml").getInt("history-retention", 50);
    }
    
    /**
     * Record a rank change in history.
     */
    public void recordRankChange(UUID playerUUID, String oldRank, String newRank, String sender, String reason) {
        FileConfiguration historyConfig = fileManager.getConfig("history.yml");
        
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String playerKey = "players." + playerUUID.toString();
        
        List<String> history = historyConfig.getStringList(playerKey + ".changes");
        if (history == null) {
            history = new ArrayList<>();
        }
        
        String changeEntry = String.format("%s|%s|%s|%s|%s", 
            timestamp, oldRank, newRank, sender, reason != null ? reason : "");
        
        history.add(changeEntry);
        
        // Limit history size
        if (history.size() > maxHistoryPerPlayer) {
            history = history.subList(history.size() - maxHistoryPerPlayer, history.size());
        }
        
        historyConfig.set(playerKey + ".changes", history);
        historyConfig.set(playerKey + ".last-updated", timestamp);
        
        fileManager.saveConfig("history.yml", historyConfig);
    }
    
    /**
     * Get player's rank history.
     */
    public List<HistoryEntry> getPlayerHistory(UUID playerUUID) {
        FileConfiguration historyConfig = fileManager.getConfig("history.yml");
        String playerKey = "players." + playerUUID.toString();
        
        List<String> history = historyConfig.getStringList(playerKey + ".changes");
        List<HistoryEntry> entries = new ArrayList<>();
        
        if (history != null) {
            for (String entry : history) {
                String[] parts = entry.split("\\|");
                if (parts.length >= 4) {
                    entries.add(new HistoryEntry(
                        parts[0], // timestamp
                        parts[1], // oldRank
                        parts[2], // newRank
                        parts[3], // sender
                        parts.length > 4 ? parts[4] : "" // reason
                    ));
                }
            }
        }
        
        return entries;
    }
    
    /**
     * Clear player's history.
     */
    public void clearPlayerHistory(UUID playerUUID) {
        FileConfiguration historyConfig = fileManager.getConfig("history.yml");
        historyConfig.set("players." + playerUUID.toString(), null);
        fileManager.saveConfig("history.yml", historyConfig);
    }
    
    /**
     * History entry.
     */
    public static class HistoryEntry {
        private final String timestamp;
        private final String oldRank;
        private final String newRank;
        private final String sender;
        private final String reason;
        
        public HistoryEntry(String timestamp, String oldRank, String newRank, String sender, String reason) {
            this.timestamp = timestamp;
            this.oldRank = oldRank;
            this.newRank = newRank;
            this.sender = sender;
            this.reason = reason;
        }
        
        public String getTimestamp() {
            return timestamp;
        }
        
        public String getOldRank() {
            return oldRank;
        }
        
        public String getNewRank() {
            return newRank;
        }
        
        public String getSender() {
            return sender;
        }
        
        public String getReason() {
            return reason;
        }
    }
}

