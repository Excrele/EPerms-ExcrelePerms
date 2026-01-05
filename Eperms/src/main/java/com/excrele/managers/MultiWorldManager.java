package com.excrele.managers;

import com.excrele.ExcrelePerms;
import com.excrele.yaml.YAMLFileManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages world-specific ranks.
 * Standalone implementation - no external dependencies.
 */
public class MultiWorldManager {
    private final ExcrelePerms plugin;
    private final YAMLFileManager fileManager;
    private final Map<UUID, Map<String, String>> worldRanks = new HashMap<>(); // player -> world -> rank
    
    public MultiWorldManager(ExcrelePerms plugin, YAMLFileManager fileManager) {
        this.plugin = plugin;
        this.fileManager = fileManager;
        loadWorldRanks();
    }
    
    /**
     * Set a player's rank for a specific world.
     */
    public boolean setWorldRank(UUID playerUUID, String world, String rank) {
        if (!plugin.rankExists(rank)) {
            return false;
        }
        
        worldRanks.computeIfAbsent(playerUUID, k -> new HashMap<>()).put(world, rank);
        saveWorldRanks();
        return true;
    }
    
    /**
     * Get a player's rank for a specific world.
     */
    public String getWorldRank(UUID playerUUID, String world) {
        Map<String, String> playerWorldRanks = worldRanks.get(playerUUID);
        if (playerWorldRanks != null) {
            return playerWorldRanks.get(world);
        }
        return null;
    }
    
    /**
     * Get a player's rank for their current world.
     */
    public String getPlayerRankForWorld(Player player) {
        String worldRank = getWorldRank(player.getUniqueId(), player.getWorld().getName());
        if (worldRank != null) {
            return worldRank;
        }
        // Fall back to global rank
        return plugin.getPlayerRank(player.getUniqueId());
    }
    
    /**
     * Remove world-specific rank.
     */
    public boolean removeWorldRank(UUID playerUUID, String world) {
        Map<String, String> playerWorldRanks = worldRanks.get(playerUUID);
        if (playerWorldRanks != null) {
            playerWorldRanks.remove(world);
            if (playerWorldRanks.isEmpty()) {
                worldRanks.remove(playerUUID);
            }
            saveWorldRanks();
            return true;
        }
        return false;
    }
    
    private void loadWorldRanks() {
        FileConfiguration config = fileManager.getConfig("world-ranks.yml");
        if (config.contains("world-ranks")) {
            for (String playerUUIDStr : config.getConfigurationSection("world-ranks").getKeys(false)) {
                try {
                    UUID playerUUID = UUID.fromString(playerUUIDStr);
                    Map<String, String> worldRanksForPlayer = new HashMap<>();
                    for (String world : config.getConfigurationSection("world-ranks." + playerUUIDStr).getKeys(false)) {
                        worldRanksForPlayer.put(world, config.getString("world-ranks." + playerUUIDStr + "." + world));
                    }
                    worldRanks.put(playerUUID, worldRanksForPlayer);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in world-ranks.yml: " + playerUUIDStr);
                }
            }
        }
    }
    
    private void saveWorldRanks() {
        FileConfiguration config = fileManager.getConfig("world-ranks.yml");
        config.set("world-ranks", null);
        for (Map.Entry<UUID, Map<String, String>> entry : worldRanks.entrySet()) {
            for (Map.Entry<String, String> worldEntry : entry.getValue().entrySet()) {
                config.set("world-ranks." + entry.getKey() + "." + worldEntry.getKey(), worldEntry.getValue());
            }
        }
        fileManager.saveConfigSync("world-ranks.yml", config);
    }
}

