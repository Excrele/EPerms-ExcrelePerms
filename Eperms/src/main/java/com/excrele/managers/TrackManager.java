package com.excrele.managers;

import com.excrele.yaml.YAMLFileManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages progression tracks.
 */
public class TrackManager {
    @SuppressWarnings("unused")
    private final JavaPlugin plugin;
    private final YAMLFileManager fileManager;
    
    public TrackManager(JavaPlugin plugin, YAMLFileManager fileManager) {
        this.plugin = plugin;
        this.fileManager = fileManager;
    }
    
    /**
     * Get all tracks.
     */
    public List<String> getAllTracks() {
        FileConfiguration config;
        if (fileManager.isUseSplitFiles()) {
            config = fileManager.getConfig("tracks.yml");
        } else {
            config = fileManager.getConfig("ranks.yml");
        }
        
        if (!config.contains("progression-tracks")) {
            return new ArrayList<>();
        }
        
        return new ArrayList<>(config.getConfigurationSection("progression-tracks").getKeys(false));
    }
    
    /**
     * Get track ranks.
     */
    public List<String> getTrack(String trackName) {
        FileConfiguration config;
        if (fileManager.isUseSplitFiles()) {
            config = fileManager.getConfig("tracks.yml");
        } else {
            config = fileManager.getConfig("ranks.yml");
        }
        
        return config.getStringList("progression-tracks." + trackName);
    }
    
    /**
     * Create a new track.
     */
    public boolean createTrack(String trackName, List<String> ranks) {
        FileConfiguration config;
        String fileName;
        
        if (fileManager.isUseSplitFiles()) {
            config = fileManager.getConfig("tracks.yml");
            fileName = "tracks.yml";
        } else {
            config = fileManager.getConfig("ranks.yml");
            fileName = "ranks.yml";
        }
        
        if (config.contains("progression-tracks." + trackName)) {
            return false; // Track already exists
        }
        
        config.set("progression-tracks." + trackName, ranks);
        return fileManager.saveConfigSync(fileName, config);
    }
    
    /**
     * Delete a track.
     */
    public boolean deleteTrack(String trackName) {
        FileConfiguration config;
        String fileName;
        
        if (fileManager.isUseSplitFiles()) {
            config = fileManager.getConfig("tracks.yml");
            fileName = "tracks.yml";
        } else {
            config = fileManager.getConfig("ranks.yml");
            fileName = "ranks.yml";
        }
        
        if (!config.contains("progression-tracks." + trackName)) {
            return false; // Track doesn't exist
        }
        
        config.set("progression-tracks." + trackName, null);
        return fileManager.saveConfigSync(fileName, config);
    }
    
    /**
     * Get track for a rank (if rank specifies a track).
     */
    public String getRankTrack(String rankName) {
        FileConfiguration ranksConfig = fileManager.getConfig("ranks.yml");
        return ranksConfig.getString("ranks." + rankName + ".track", "defaultTrack");
    }
    
    /**
     * Set track for a rank.
     */
    public boolean setRankTrack(String rankName, String trackName) {
        FileConfiguration ranksConfig = fileManager.getConfig("ranks.yml");
        ranksConfig.set("ranks." + rankName + ".track", trackName);
        return fileManager.saveConfigSync("ranks.yml", ranksConfig);
    }
    
    /**
     * Get next rank in track.
     */
    public String getNextRank(String currentRank, String trackName) {
        List<String> track = getTrack(trackName);
        if (track.isEmpty()) return null;
        
        int index = track.indexOf(currentRank);
        if (index == -1 || index == track.size() - 1) {
            return null;
        }
        
        return track.get(index + 1);
    }
    
    /**
     * Get previous rank in track.
     */
    public String getPreviousRank(String currentRank, String trackName) {
        List<String> track = getTrack(trackName);
        if (track.isEmpty()) return null;
        
        int index = track.indexOf(currentRank);
        if (index <= 0) {
            return null;
        }
        
        return track.get(index - 1);
    }
}


