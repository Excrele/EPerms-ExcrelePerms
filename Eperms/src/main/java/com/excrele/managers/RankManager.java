package com.excrele.managers;

import com.excrele.yaml.YAMLFileManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages rank creation, deletion, and editing.
 */
public class RankManager {
    @SuppressWarnings("unused")
    private final JavaPlugin plugin;
    private final YAMLFileManager fileManager;
    
    public RankManager(JavaPlugin plugin, YAMLFileManager fileManager) {
        this.plugin = plugin;
        this.fileManager = fileManager;
    }
    
    /**
     * Create a new rank.
     */
    public boolean createRank(String rankName, String prefix, String suffix, List<String> permissions, List<String> inheritance) {
        FileConfiguration ranksConfig = fileManager.getConfig("ranks.yml");
        
        if (ranksConfig.contains("ranks." + rankName)) {
            return false; // Rank already exists
        }
        
        ranksConfig.set("ranks." + rankName + ".info.prefix", prefix != null ? prefix : "");
        ranksConfig.set("ranks." + rankName + ".info.suffix", suffix != null ? suffix : "");
        ranksConfig.set("ranks." + rankName + ".permissions", permissions != null ? permissions : new ArrayList<>());
        ranksConfig.set("ranks." + rankName + ".inheritance", inheritance != null ? inheritance : new ArrayList<>());
        ranksConfig.set("ranks." + rankName + ".priority", 0);
        
        return fileManager.saveConfigSync("ranks.yml", ranksConfig);
    }
    
    /**
     * Delete a rank.
     */
    public boolean deleteRank(String rankName) {
        FileConfiguration ranksConfig = fileManager.getConfig("ranks.yml");
        
        if (!ranksConfig.contains("ranks." + rankName)) {
            return false; // Rank doesn't exist
        }
        
        ranksConfig.set("ranks." + rankName, null);
        return fileManager.saveConfigSync("ranks.yml", ranksConfig);
    }
    
    /**
     * Clone a rank.
     */
    public boolean cloneRank(String sourceRank, String targetRank) {
        FileConfiguration ranksConfig = fileManager.getConfig("ranks.yml");
        
        if (!ranksConfig.contains("ranks." + sourceRank)) {
            return false; // Source rank doesn't exist
        }
        
        if (ranksConfig.contains("ranks." + targetRank)) {
            return false; // Target rank already exists
        }
        
        // Copy all settings from source to target
        ranksConfig.set("ranks." + targetRank + ".info.prefix", 
            ranksConfig.getString("ranks." + sourceRank + ".info.prefix", ""));
        ranksConfig.set("ranks." + targetRank + ".info.suffix", 
            ranksConfig.getString("ranks." + sourceRank + ".info.suffix", ""));
        ranksConfig.set("ranks." + targetRank + ".permissions", 
            ranksConfig.getStringList("ranks." + sourceRank + ".permissions"));
        ranksConfig.set("ranks." + targetRank + ".inheritance", 
            ranksConfig.getStringList("ranks." + sourceRank + ".inheritance"));
        ranksConfig.set("ranks." + targetRank + ".priority", 
            ranksConfig.getInt("ranks." + sourceRank + ".priority", 0));
        ranksConfig.set("ranks." + targetRank + ".track", 
            ranksConfig.getString("ranks." + sourceRank + ".track", "defaultTrack"));
        
        return fileManager.saveConfigSync("ranks.yml", ranksConfig);
    }
    
    /**
     * Edit rank property.
     */
    public boolean editRank(String rankName, String property, String value) {
        FileConfiguration ranksConfig = fileManager.getConfig("ranks.yml");
        
        if (!ranksConfig.contains("ranks." + rankName)) {
            return false; // Rank doesn't exist
        }
        
        switch (property.toLowerCase()) {
            case "prefix":
                ranksConfig.set("ranks." + rankName + ".info.prefix", value);
                break;
            case "suffix":
                ranksConfig.set("ranks." + rankName + ".info.suffix", value);
                break;
            case "priority":
                try {
                    int priority = Integer.parseInt(value);
                    ranksConfig.set("ranks." + rankName + ".priority", priority);
                } catch (NumberFormatException e) {
                    return false;
                }
                break;
            case "track":
                ranksConfig.set("ranks." + rankName + ".track", value);
                break;
            default:
                return false;
        }
        
        return fileManager.saveConfigSync("ranks.yml", ranksConfig);
    }
    
    /**
     * Add permission to rank.
     */
    public boolean addPermission(String rankName, String permission) {
        FileConfiguration ranksConfig = fileManager.getConfig("ranks.yml");
        
        if (!ranksConfig.contains("ranks." + rankName)) {
            return false;
        }
        
        List<String> permissions = ranksConfig.getStringList("ranks." + rankName + ".permissions");
        if (!permissions.contains(permission)) {
            permissions.add(permission);
            ranksConfig.set("ranks." + rankName + ".permissions", permissions);
            return fileManager.saveConfigSync("ranks.yml", ranksConfig);
        }
        
        return true; // Already exists
    }
    
    /**
     * Remove permission from rank.
     */
    public boolean removePermission(String rankName, String permission) {
        FileConfiguration ranksConfig = fileManager.getConfig("ranks.yml");
        
        if (!ranksConfig.contains("ranks." + rankName)) {
            return false;
        }
        
        List<String> permissions = ranksConfig.getStringList("ranks." + rankName + ".permissions");
        permissions.remove(permission);
        ranksConfig.set("ranks." + rankName + ".permissions", permissions);
        return fileManager.saveConfigSync("ranks.yml", ranksConfig);
    }
    
    /**
     * Add inheritance to rank.
     */
    public boolean addInheritance(String rankName, String parentRank) {
        FileConfiguration ranksConfig = fileManager.getConfig("ranks.yml");
        
        if (!ranksConfig.contains("ranks." + rankName) || !ranksConfig.contains("ranks." + parentRank)) {
            return false;
        }
        
        List<String> inheritance = ranksConfig.getStringList("ranks." + rankName + ".inheritance");
        if (!inheritance.contains(parentRank)) {
            inheritance.add(parentRank);
            ranksConfig.set("ranks." + rankName + ".inheritance", inheritance);
            return fileManager.saveConfigSync("ranks.yml", ranksConfig);
        }
        
        return true; // Already exists
    }
    
    /**
     * Remove inheritance from rank.
     */
    public boolean removeInheritance(String rankName, String parentRank) {
        FileConfiguration ranksConfig = fileManager.getConfig("ranks.yml");
        
        if (!ranksConfig.contains("ranks." + rankName)) {
            return false;
        }
        
        List<String> inheritance = ranksConfig.getStringList("ranks." + rankName + ".inheritance");
        inheritance.remove(parentRank);
        ranksConfig.set("ranks." + rankName + ".inheritance", inheritance);
        return fileManager.saveConfigSync("ranks.yml", ranksConfig);
    }
}


