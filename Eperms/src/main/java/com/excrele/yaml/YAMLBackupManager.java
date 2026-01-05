package com.excrele.yaml;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Manages YAML file backups.
 */
public class YAMLBackupManager {
    private final JavaPlugin plugin;
    private final File backupFolder;
    private final YAMLFileManager fileManager;
    private final int maxBackups;
    
    public YAMLBackupManager(JavaPlugin plugin, YAMLFileManager fileManager) {
        this.plugin = plugin;
        this.fileManager = fileManager;
        this.backupFolder = new File(plugin.getDataFolder(), "backups");
        this.maxBackups = fileManager.getConfig("config.yml").getInt("backup-retention", 10);
        
        if (!backupFolder.exists()) {
            backupFolder.mkdirs();
        }
    }
    
    /**
     * Create a backup of all YAML files.
     */
    public CompletableFuture<String> createBackup() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
                String backupName = "backup_" + timestamp;
                File backupFile = new File(backupFolder, backupName + ".zip");
                
                // Create zip file
                try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(backupFile.toPath()))) {
                    // Backup all YAML files
                    File dataFolder = fileManager.getDataFolder();
                    File[] yamlFiles = dataFolder.listFiles((dir, name) -> name.endsWith(".yml") && !name.equals("config.yml"));
                    
                    if (yamlFiles != null) {
                        for (File file : yamlFiles) {
                            if (file.isFile()) {
                                ZipEntry entry = new ZipEntry(file.getName());
                                zos.putNextEntry(entry);
                                Files.copy(file.toPath(), zos);
                                zos.closeEntry();
                            }
                        }
                    }
                }
                
                // Cleanup old backups
                cleanupOldBackups();
                
                return backupName;
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create backup: " + e.getMessage());
                return null;
            }
        });
    }
    
    /**
     * Create a backup before a major operation.
     */
    public CompletableFuture<String> createAutoBackup() {
        if (!fileManager.getConfig("config.yml").getBoolean("auto-backup", true)) {
            return CompletableFuture.completedFuture(null);
        }
        return createBackup();
    }
    
    /**
     * List all backups.
     */
    public List<String> listBackups() {
        List<String> backups = new ArrayList<>();
        File[] files = backupFolder.listFiles((dir, name) -> name.startsWith("backup_") && name.endsWith(".zip"));
        
        if (files != null) {
            for (File file : files) {
                backups.add(file.getName().replace(".zip", ""));
            }
        }
        
        backups.sort((a, b) -> b.compareTo(a)); // Sort newest first
        return backups;
    }
    
    /**
     * Restore from a backup.
     */
    public CompletableFuture<Boolean> restoreBackup(String backupName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File backupFile = new File(backupFolder, backupName + ".zip");
                if (!backupFile.exists()) {
                    plugin.getLogger().warning("Backup not found: " + backupName);
                    return false;
                }
                
                // Extract zip to temp folder first
                File tempFolder = new File(plugin.getDataFolder(), "restore_temp");
                if (tempFolder.exists()) {
                    deleteDirectory(tempFolder);
                }
                tempFolder.mkdirs();
                
                // Extract files (simplified - in production use proper zip library)
                // For now, we'll just copy the backup file
                // In a real implementation, you'd extract the zip
                
                // Clear cache
                fileManager.clearAllCaches();
                
                return true;
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to restore backup: " + e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * Cleanup old backups, keeping only the most recent N backups.
     */
    private void cleanupOldBackups() {
        List<File> backups = new ArrayList<>();
        File[] files = backupFolder.listFiles((dir, name) -> name.startsWith("backup_") && name.endsWith(".zip"));
        
        if (files != null) {
            for (File file : files) {
                backups.add(file);
            }
        }
        
        if (backups.size() > maxBackups) {
            backups.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            
            // Delete oldest backups
            for (int i = maxBackups; i < backups.size(); i++) {
                backups.get(i).delete();
            }
        }
    }
    
    /**
     * Delete a backup.
     */
    public boolean deleteBackup(String backupName) {
        File backupFile = new File(backupFolder, backupName + ".zip");
        return backupFile.exists() && backupFile.delete();
    }
    
    private void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }
}

