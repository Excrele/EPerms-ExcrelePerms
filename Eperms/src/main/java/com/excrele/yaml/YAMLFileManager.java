package com.excrele.yaml;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Manages YAML file operations with async support and caching.
 */
public class YAMLFileManager {
    private final JavaPlugin plugin;
    private final Map<String, FileConfiguration> configCache;
    private final Map<String, Long> lastModified;
    private final File dataFolder;
    private boolean useSplitFiles;
    
    public YAMLFileManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = plugin.getDataFolder();
        this.configCache = new ConcurrentHashMap<>();
        this.lastModified = new ConcurrentHashMap<>();
        this.useSplitFiles = false;
        
        // Load config to check if split files are enabled
        loadConfig();
    }
    
    /**
     * Load the main config.yml file.
     */
    public void loadConfig() {
        File configFile = new File(dataFolder, "config.yml");
        if (!configFile.exists()) {
            // Create default config
            try {
                configFile.getParentFile().mkdirs();
                configFile.createNewFile();
                FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
                config.set("use-split-files", false);
                config.set("auto-backup", true);
                config.set("backup-retention", 10);
                config.set("async-operations", true);
                config.set("cache-enabled", true);
                config.set("lazy-loading", true);
                config.save(configFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create config.yml: " + e.getMessage());
            }
        }
        
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        this.useSplitFiles = config.getBoolean("use-split-files", false);
    }
    
    /**
     * Get configuration file (cached).
     */
    public FileConfiguration getConfig(String fileName) {
        File file = new File(dataFolder, fileName);
        
        // Check cache
        if (configCache.containsKey(fileName)) {
            long currentModified = file.lastModified();
            if (lastModified.containsKey(fileName) && lastModified.get(fileName) == currentModified) {
                return configCache.get(fileName);
            }
        }
        
        // Load from file
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create " + fileName + ": " + e.getMessage());
                return new YamlConfiguration();
            }
        }
        
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        configCache.put(fileName, config);
        lastModified.put(fileName, file.lastModified());
        return config;
    }
    
    /**
     * Save configuration file (async if enabled).
     */
    public CompletableFuture<Boolean> saveConfig(String fileName, FileConfiguration config) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File file = new File(dataFolder, fileName);
                
                // Atomic write: write to temp file, then rename
                File tempFile = new File(dataFolder, fileName + ".tmp");
                config.save(tempFile);
                
                // Atomic move
                Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                
                // Update cache
                configCache.put(fileName, config);
                lastModified.put(fileName, file.lastModified());
                
                return true;
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save " + fileName + ": " + e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * Save configuration synchronously (for critical operations).
     */
    public boolean saveConfigSync(String fileName, FileConfiguration config) {
        try {
            File file = new File(dataFolder, fileName);
            
            // Atomic write
            File tempFile = new File(dataFolder, fileName + ".tmp");
            config.save(tempFile);
            Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            
            // Update cache
            configCache.put(fileName, config);
            lastModified.put(fileName, file.lastModified());
            
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save " + fileName + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Clear cache for a specific file.
     */
    public void clearCache(String fileName) {
        configCache.remove(fileName);
        lastModified.remove(fileName);
    }
    
    /**
     * Clear all caches.
     */
    public void clearAllCaches() {
        configCache.clear();
        lastModified.clear();
    }
    
    /**
     * Check if file has been modified since last load.
     */
    public boolean isFileModified(String fileName) {
        File file = new File(dataFolder, fileName);
        if (!file.exists()) return false;
        
        long currentModified = file.lastModified();
        if (!lastModified.containsKey(fileName)) return true;
        return lastModified.get(fileName) != currentModified;
    }
    
    /**
     * Reload configuration from file.
     */
    public FileConfiguration reloadConfig(String fileName) {
        clearCache(fileName);
        return getConfig(fileName);
    }
    
    public boolean isUseSplitFiles() {
        return useSplitFiles;
    }
    
    public void setUseSplitFiles(boolean useSplitFiles) {
        this.useSplitFiles = useSplitFiles;
        File configFile = new File(dataFolder, "config.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        config.set("use-split-files", useSplitFiles);
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to update config.yml: " + e.getMessage());
        }
    }
    
    public File getDataFolder() {
        return dataFolder;
    }
}

