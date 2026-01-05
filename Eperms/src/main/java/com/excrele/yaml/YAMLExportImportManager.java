package com.excrele.yaml;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Manages YAML export and import operations.
 */
public class YAMLExportImportManager {
    private final JavaPlugin plugin;
    private final YAMLFileManager fileManager;
    
    public YAMLExportImportManager(JavaPlugin plugin, YAMLFileManager fileManager) {
        this.plugin = plugin;
        this.fileManager = fileManager;
    }
    
    /**
     * Export ranks to a YAML file.
     */
    public CompletableFuture<Boolean> exportRanks(File exportFile) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                FileConfiguration ranksConfig = fileManager.getConfig("ranks.yml");
                
                // Create export directory if needed
                exportFile.getParentFile().mkdirs();
                
                // Copy ranks section
                FileConfiguration exportConfig = new org.bukkit.configuration.file.YamlConfiguration();
                if (ranksConfig.contains("ranks")) {
                    exportConfig.set("ranks", ranksConfig.getConfigurationSection("ranks"));
                }
                
                exportConfig.save(exportFile);
                return true;
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to export ranks: " + e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * Export players to a YAML file.
     */
    public CompletableFuture<Boolean> exportPlayers(File exportFile) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                FileConfiguration playersConfig;
                if (fileManager.isUseSplitFiles()) {
                    playersConfig = fileManager.getConfig("players.yml");
                } else {
                    playersConfig = fileManager.getConfig("ranks.yml");
                }
                
                exportFile.getParentFile().mkdirs();
                
                FileConfiguration exportConfig = new org.bukkit.configuration.file.YamlConfiguration();
                if (playersConfig.contains("players")) {
                    exportConfig.set("players", playersConfig.getConfigurationSection("players"));
                }
                
                exportConfig.save(exportFile);
                return true;
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to export players: " + e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * Import from a YAML file.
     */
    public CompletableFuture<Boolean> importFromFile(File importFile, boolean merge) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!importFile.exists()) {
                    plugin.getLogger().warning("Import file not found: " + importFile.getName());
                    return false;
                }
                
                // Create backup before import
                YAMLBackupManager backupManager = new YAMLBackupManager(plugin, fileManager);
                backupManager.createAutoBackup();
                
                FileConfiguration importConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(importFile);
                
                if (importConfig.contains("ranks")) {
                    FileConfiguration ranksConfig = fileManager.getConfig("ranks.yml");
                    if (merge) {
                        // Merge ranks
                        for (String rankKey : importConfig.getConfigurationSection("ranks").getKeys(false)) {
                            ranksConfig.set("ranks." + rankKey, importConfig.getConfigurationSection("ranks." + rankKey));
                        }
                    } else {
                        // Replace ranks
                        ranksConfig.set("ranks", importConfig.getConfigurationSection("ranks"));
                    }
                    fileManager.saveConfigSync("ranks.yml", ranksConfig);
                }
                
                if (importConfig.contains("players")) {
                    FileConfiguration playersConfig;
                    String fileName = fileManager.isUseSplitFiles() ? "players.yml" : "ranks.yml";
                    playersConfig = fileManager.getConfig(fileName);
                    
                    if (merge) {
                        // Merge players
                        for (String playerKey : importConfig.getConfigurationSection("players").getKeys(false)) {
                            playersConfig.set("players." + playerKey, importConfig.getConfigurationSection("players." + playerKey));
                        }
                    } else {
                        // Replace players
                        playersConfig.set("players", importConfig.getConfigurationSection("players"));
                    }
                    fileManager.saveConfigSync(fileName, playersConfig);
                }
                
                // Clear cache
                fileManager.clearAllCaches();
                
                return true;
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to import from file: " + e.getMessage());
                return false;
            }
        });
    }
}

