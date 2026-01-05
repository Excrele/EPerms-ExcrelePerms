package com.excrele.managers;

import com.excrele.yaml.YAMLFileManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages temporary ranks with expiration.
 */
public class TemporaryRankManager {
    private final JavaPlugin plugin;
    private final YAMLFileManager fileManager;
    private final Map<UUID, TemporaryRankInfo> temporaryRanks;
    @SuppressWarnings("unused")
    private BukkitTask expirationTask;
    
    public TemporaryRankManager(JavaPlugin plugin, YAMLFileManager fileManager) {
        this.plugin = plugin;
        this.fileManager = fileManager;
        this.temporaryRanks = new HashMap<>();
        startExpirationChecker();
    }
    
    /**
     * Assign a temporary rank to a player.
     */
    public boolean assignTemporaryRank(UUID playerUUID, String rank, long durationMillis) {
        FileConfiguration tempConfig = fileManager.getConfig("temporary-ranks.yml");
        
        long expirationTime = System.currentTimeMillis() + durationMillis;
        String expirationTimeStr = String.valueOf(expirationTime);
        
        // Store original rank
        FileConfiguration ranksConfig = fileManager.getConfig("ranks.yml");
        String originalRank = ranksConfig.getString("players." + playerUUID + ".rank", "default");
        
        tempConfig.set("players." + playerUUID + ".rank", rank);
        tempConfig.set("players." + playerUUID + ".original-rank", originalRank);
        tempConfig.set("players." + playerUUID + ".expiration", expirationTimeStr);
        tempConfig.set("players." + playerUUID + ".assigned-at", System.currentTimeMillis());
        
        fileManager.saveConfigSync("temporary-ranks.yml", tempConfig);
        
        // Store in memory
        temporaryRanks.put(playerUUID, new TemporaryRankInfo(rank, originalRank, expirationTime));
        
        return true;
    }
    
    /**
     * Parse duration string (e.g., "1d", "2h", "30m", "1w").
     */
    public long parseDuration(String durationStr) {
        Pattern pattern = Pattern.compile("(\\d+)([dwhms])", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(durationStr);
        
        long totalMillis = 0;
        while (matcher.find()) {
            int amount = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2).toLowerCase();
            
            switch (unit) {
                case "w":
                    totalMillis += amount * 7L * 24 * 60 * 60 * 1000;
                    break;
                case "d":
                    totalMillis += amount * 24L * 60 * 60 * 1000;
                    break;
                case "h":
                    totalMillis += amount * 60L * 60 * 1000;
                    break;
                case "m":
                    totalMillis += amount * 60 * 1000;
                    break;
                case "s":
                    totalMillis += amount * 1000L;
                    break;
            }
        }
        
        return totalMillis;
    }
    
    /**
     * Cancel a temporary rank.
     */
    public boolean cancelTemporaryRank(UUID playerUUID) {
        FileConfiguration tempConfig = fileManager.getConfig("temporary-ranks.yml");
        
        if (!tempConfig.contains("players." + playerUUID)) {
            return false;
        }
        
        String originalRank = tempConfig.getString("players." + playerUUID + ".original-rank", "default");
        
        // Restore original rank
        FileConfiguration ranksConfig = fileManager.getConfig("ranks.yml");
        ranksConfig.set("players." + playerUUID + ".rank", originalRank);
        fileManager.saveConfigSync("ranks.yml", ranksConfig);
        
        // Remove from temp config
        tempConfig.set("players." + playerUUID, null);
        fileManager.saveConfigSync("temporary-ranks.yml", tempConfig);
        
        temporaryRanks.remove(playerUUID);
        
        return true;
    }
    
    /**
     * Get temporary rank info.
     */
    public TemporaryRankInfo getTemporaryRank(UUID playerUUID) {
        return temporaryRanks.get(playerUUID);
    }
    
    /**
     * Check if player has temporary rank.
     */
    public boolean hasTemporaryRank(UUID playerUUID) {
        return temporaryRanks.containsKey(playerUUID) && 
               temporaryRanks.get(playerUUID).getExpirationTime() > System.currentTimeMillis();
    }
    
    /**
     * Start expiration checker task.
     */
    private void startExpirationChecker() {
        expirationTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long currentTime = System.currentTimeMillis();
            Map<UUID, TemporaryRankInfo> toRemove = new HashMap<>();
            
            for (Map.Entry<UUID, TemporaryRankInfo> entry : temporaryRanks.entrySet()) {
                if (entry.getValue().getExpirationTime() <= currentTime) {
                    toRemove.put(entry.getKey(), entry.getValue());
                }
            }
            
            // Expire ranks
            for (Map.Entry<UUID, TemporaryRankInfo> entry : toRemove.entrySet()) {
                UUID playerUUID = entry.getKey();
                TemporaryRankInfo info = entry.getValue();
                
                // Restore original rank
                FileConfiguration ranksConfig = fileManager.getConfig("ranks.yml");
                ranksConfig.set("players." + playerUUID + ".rank", info.getOriginalRank());
                fileManager.saveConfigSync("ranks.yml", ranksConfig);
                
                // Remove from temp config
                FileConfiguration tempConfig = fileManager.getConfig("temporary-ranks.yml");
                tempConfig.set("players." + playerUUID, null);
                fileManager.saveConfigSync("temporary-ranks.yml", tempConfig);
                
                temporaryRanks.remove(playerUUID);
                
                // Notify player if online
                Player player = plugin.getServer().getPlayer(playerUUID);
                if (player != null) {
                    player.sendMessage(org.bukkit.ChatColor.YELLOW + 
                        "Your temporary rank has expired. You have been restored to: " + info.getOriginalRank());
                }
            }
        }, 0L, 20L * 60); // Check every minute
    }
    
    /**
     * Get all active temporary ranks.
     */
    public Map<UUID, TemporaryRankInfo> getAllTemporaryRanks() {
        Map<UUID, TemporaryRankInfo> activeRanks = new HashMap<>();
        long currentTime = System.currentTimeMillis();
        
        for (Map.Entry<UUID, TemporaryRankInfo> entry : temporaryRanks.entrySet()) {
            if (entry.getValue().getExpirationTime() > currentTime) {
                activeRanks.put(entry.getKey(), entry.getValue());
            }
        }
        
        return activeRanks;
    }
    
    /**
     * Load temporary ranks from file.
     */
    public void loadTemporaryRanks() {
        FileConfiguration tempConfig = fileManager.getConfig("temporary-ranks.yml");
        
        if (!tempConfig.contains("players")) {
            return;
        }
        
        for (String playerKey : tempConfig.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID playerUUID = UUID.fromString(playerKey);
                String rank = tempConfig.getString("players." + playerKey + ".rank");
                String originalRank = tempConfig.getString("players." + playerKey + ".original-rank", "default");
                long expiration = tempConfig.getLong("players." + playerKey + ".expiration", 0);
                
                if (expiration > System.currentTimeMillis()) {
                    temporaryRanks.put(playerUUID, new TemporaryRankInfo(rank, originalRank, expiration));
                } else {
                    // Expired, clean up
                    tempConfig.set("players." + playerKey, null);
                }
            } catch (IllegalArgumentException e) {
                // Invalid UUID, skip
            }
        }
        
        fileManager.saveConfigSync("temporary-ranks.yml", tempConfig);
    }
    
    /**
     * Temporary rank info.
     */
    public static class TemporaryRankInfo {
        private final String rank;
        private final String originalRank;
        private final long expirationTime;
        
        public TemporaryRankInfo(String rank, String originalRank, long expirationTime) {
            this.rank = rank;
            this.originalRank = originalRank;
            this.expirationTime = expirationTime;
        }
        
        public String getRank() {
            return rank;
        }
        
        public String getOriginalRank() {
            return originalRank;
        }
        
        public long getExpirationTime() {
            return expirationTime;
        }
        
        public long getRemainingTime() {
            return Math.max(0, expirationTime - System.currentTimeMillis());
        }
    }
}


