package com.excrele.yaml;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates YAML configuration files.
 */
public class YAMLValidationManager {
    private final JavaPlugin plugin;
    private final YAMLFileManager fileManager;
    
    public YAMLValidationManager(JavaPlugin plugin, YAMLFileManager fileManager) {
        this.plugin = plugin;
        this.fileManager = fileManager;
    }
    
    /**
     * Validation result.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;
        
        public ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.errors = errors;
            this.warnings = warnings;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public List<String> getErrors() {
            return errors;
        }
        
        public List<String> getWarnings() {
            return warnings;
        }
    }
    
    /**
     * Validate all configuration files.
     */
    public ValidationResult validateAll() {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Validate ranks
        ValidationResult ranksResult = validateRanks();
        errors.addAll(ranksResult.getErrors());
        warnings.addAll(ranksResult.getWarnings());
        
        // Validate players
        ValidationResult playersResult = validatePlayers();
        errors.addAll(playersResult.getErrors());
        warnings.addAll(playersResult.getWarnings());
        
        // Validate tracks
        ValidationResult tracksResult = validateTracks();
        errors.addAll(tracksResult.getErrors());
        warnings.addAll(tracksResult.getWarnings());
        
        // Check for circular inheritance
        List<String> circularErrors = checkCircularInheritance();
        errors.addAll(circularErrors);
        
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    /**
     * Validate ranks configuration.
     */
    public ValidationResult validateRanks() {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        FileConfiguration ranksConfig;
        if (fileManager.isUseSplitFiles()) {
            ranksConfig = fileManager.getConfig("ranks.yml");
        } else {
            ranksConfig = fileManager.getConfig("ranks.yml");
        }
        
        if (!ranksConfig.contains("ranks")) {
            warnings.add("No ranks defined in configuration");
            return new ValidationResult(true, errors, warnings);
        }
        
        Set<String> rankNames = new HashSet<>();
        for (String rankKey : ranksConfig.getConfigurationSection("ranks").getKeys(false)) {
            // Check for duplicate rank names
            if (rankNames.contains(rankKey)) {
                errors.add("Duplicate rank name: " + rankKey);
            }
            rankNames.add(rankKey);
            
            // Validate rank structure
            List<String> inheritance = ranksConfig.getStringList("ranks." + rankKey + ".inheritance");
            
            // Check inheritance references
            for (String inheritedRank : inheritance) {
                if (!ranksConfig.contains("ranks." + inheritedRank)) {
                    errors.add("Rank '" + rankKey + "' inherits from non-existent rank: " + inheritedRank);
                }
            }
        }
        
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    /**
     * Validate players configuration.
     */
    public ValidationResult validatePlayers() {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        FileConfiguration playersConfig;
        if (fileManager.isUseSplitFiles()) {
            playersConfig = fileManager.getConfig("players.yml");
        } else {
            playersConfig = fileManager.getConfig("ranks.yml");
        }
        
        FileConfiguration ranksConfig = fileManager.getConfig("ranks.yml");
        
        if (!playersConfig.contains("players")) {
            return new ValidationResult(true, errors, warnings);
        }
        
        for (String playerKey : playersConfig.getConfigurationSection("players").getKeys(false)) {
            String rank = playersConfig.getString("players." + playerKey + ".rank", "default");
            
            // Validate UUID format
            try {
                java.util.UUID.fromString(playerKey);
            } catch (IllegalArgumentException e) {
                errors.add("Invalid UUID format in players section: " + playerKey);
            }
            
            // Check if rank exists
            if (!ranksConfig.contains("ranks." + rank)) {
                errors.add("Player " + playerKey + " has non-existent rank: " + rank);
            }
        }
        
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    /**
     * Validate tracks configuration.
     */
    public ValidationResult validateTracks() {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        FileConfiguration config;
        if (fileManager.isUseSplitFiles()) {
            config = fileManager.getConfig("tracks.yml");
        } else {
            config = fileManager.getConfig("ranks.yml");
        }
        
        FileConfiguration ranksConfig = fileManager.getConfig("ranks.yml");
        
        if (!config.contains("progression-tracks")) {
            warnings.add("No progression tracks defined");
            return new ValidationResult(true, errors, warnings);
        }
        
        for (String trackKey : config.getConfigurationSection("progression-tracks").getKeys(false)) {
            List<String> track = config.getStringList("progression-tracks." + trackKey);
            
            if (track.isEmpty()) {
                warnings.add("Track '" + trackKey + "' is empty");
                continue;
            }
            
            // Check if all ranks in track exist
            for (String rank : track) {
                if (!ranksConfig.contains("ranks." + rank)) {
                    errors.add("Track '" + trackKey + "' contains non-existent rank: " + rank);
                }
            }
            
            // Check for duplicates in track
            Set<String> seen = new HashSet<>();
            for (String rank : track) {
                if (seen.contains(rank)) {
                    errors.add("Track '" + trackKey + "' contains duplicate rank: " + rank);
                }
                seen.add(rank);
            }
        }
        
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    /**
     * Check for circular inheritance.
     */
    public List<String> checkCircularInheritance() {
        List<String> errors = new ArrayList<>();
        
        FileConfiguration ranksConfig = fileManager.getConfig("ranks.yml");
        if (!ranksConfig.contains("ranks")) {
            return errors;
        }
        
        for (String rankKey : ranksConfig.getConfigurationSection("ranks").getKeys(false)) {
            Set<String> visited = new HashSet<>();
            if (hasCircularInheritance(rankKey, ranksConfig, visited)) {
                errors.add("Circular inheritance detected for rank: " + rankKey);
            }
        }
        
        return errors;
    }
    
    private boolean hasCircularInheritance(String rank, FileConfiguration config, Set<String> visited) {
        if (visited.contains(rank)) {
            return true; // Circular reference detected
        }
        
        visited.add(rank);
        List<String> inheritance = config.getStringList("ranks." + rank + ".inheritance");
        
        for (String inheritedRank : inheritance) {
            if (hasCircularInheritance(inheritedRank, config, new HashSet<>(visited))) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Format YAML file (ensure consistent formatting).
     */
    public boolean formatYAML(String fileName) {
        try {
            FileConfiguration config = fileManager.getConfig(fileName);
            return fileManager.saveConfigSync(fileName, config);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to format " + fileName + ": " + e.getMessage());
            return false;
        }
    }
}

