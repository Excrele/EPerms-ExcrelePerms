package com.excrele.managers;

import com.excrele.ExcrelePerms;
import com.excrele.yaml.YAMLFileManager;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles migration from other permission plugins.
 * Standalone implementation - works with YAML exports from other plugins.
 */
public class MigrationManager {
    private final YAMLFileManager fileManager;
    
    public MigrationManager(ExcrelePerms plugin, YAMLFileManager fileManager) {
        this.fileManager = fileManager;
    }
    
    /**
     * Migrate from a YAML file (generic format).
     */
    public MigrationResult migrateFromYAML(File sourceFile, String pluginType) {
        MigrationResult result = new MigrationResult();
        
        try {
            FileConfiguration sourceConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(sourceFile);
            
            switch (pluginType.toLowerCase()) {
                case "luckperms":
                    return migrateFromLuckPerms(sourceConfig, result);
                case "groupmanager":
                    return migrateFromGroupManager(sourceConfig, result);
                case "permissionsex":
                    return migrateFromPermissionsEx(sourceConfig, result);
                default:
                    result.addError("Unknown plugin type: " + pluginType);
                    return result;
            }
        } catch (Exception e) {
            result.addError("Error reading source file: " + e.getMessage());
            return result;
        }
    }
    
    private MigrationResult migrateFromLuckPerms(FileConfiguration source, MigrationResult result) {
        // LuckPerms YAML format migration
        FileConfiguration ranksConfig = fileManager.getConfig("ranks.yml");
        if (source.contains("groups")) {
            for (String groupName : source.getConfigurationSection("groups").getKeys(false)) {
                // Create rank in ranks.yml
                ranksConfig.set("ranks." + groupName + ".info.prefix", "");
                ranksConfig.set("ranks." + groupName + ".info.suffix", "");
                ranksConfig.set("ranks." + groupName + ".priority", 0);
                result.addMigrated("rank", groupName);
            }
            fileManager.saveConfigSync("ranks.yml", ranksConfig);
        }
        return result;
    }
    
    private MigrationResult migrateFromGroupManager(FileConfiguration source, MigrationResult result) {
        // GroupManager format migration
        FileConfiguration ranksConfig = fileManager.getConfig("ranks.yml");
        if (source.contains("groups")) {
            for (String groupName : source.getConfigurationSection("groups").getKeys(false)) {
                ranksConfig.set("ranks." + groupName + ".info.prefix", "");
                ranksConfig.set("ranks." + groupName + ".info.suffix", "");
                ranksConfig.set("ranks." + groupName + ".priority", 0);
                result.addMigrated("rank", groupName);
            }
            fileManager.saveConfigSync("ranks.yml", ranksConfig);
        }
        return result;
    }
    
    private MigrationResult migrateFromPermissionsEx(FileConfiguration source, MigrationResult result) {
        // PermissionsEx format migration
        FileConfiguration ranksConfig = fileManager.getConfig("ranks.yml");
        if (source.contains("groups")) {
            for (String groupName : source.getConfigurationSection("groups").getKeys(false)) {
                ranksConfig.set("ranks." + groupName + ".info.prefix", "");
                ranksConfig.set("ranks." + groupName + ".info.suffix", "");
                ranksConfig.set("ranks." + groupName + ".priority", 0);
                result.addMigrated("rank", groupName);
            }
            fileManager.saveConfigSync("ranks.yml", ranksConfig);
        }
        return result;
    }
    
    public static class MigrationResult {
        private final List<String> migrated = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        
        public void addMigrated(String type, String name) {
            migrated.add(type + ": " + name);
        }
        
        public void addError(String error) {
            errors.add(error);
        }
        
        public void addWarning(String warning) {
            warnings.add(warning);
        }
        
        public List<String> getMigrated() {
            return migrated;
        }
        
        public List<String> getErrors() {
            return errors;
        }
        
        public List<String> getWarnings() {
            return warnings;
        }
        
        public boolean isSuccess() {
            return errors.isEmpty() && !migrated.isEmpty();
        }
    }
}

