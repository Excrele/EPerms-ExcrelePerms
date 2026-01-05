package com.excrele.yaml;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages file-based logging with log rotation.
 */
public class YAMLLoggingManager {
    private final JavaPlugin plugin;
    private final File logsFolder;
    private final ReentrantLock lock;
    private String currentLogFile;
    private final SimpleDateFormat dateFormat;
    private final SimpleDateFormat logDateFormat;
    private LogRotationType rotationType;
    private int maxLogFiles;
    
    public enum LogRotationType {
        DAILY, WEEKLY, MONTHLY, NONE
    }
    
    public enum LogLevel {
        INFO, WARNING, ERROR
    }
    
    public YAMLLoggingManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logsFolder = new File(plugin.getDataFolder(), "logs");
        this.lock = new ReentrantLock();
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        this.logDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        
        // Load config
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (configFile.exists()) {
            org.bukkit.configuration.file.FileConfiguration config = 
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile);
            String rotationStr = config.getString("log-rotation", "DAILY").toUpperCase();
            try {
                this.rotationType = LogRotationType.valueOf(rotationStr);
            } catch (IllegalArgumentException e) {
                this.rotationType = LogRotationType.DAILY;
            }
            this.maxLogFiles = config.getInt("max-log-files", 30);
        } else {
            this.rotationType = LogRotationType.DAILY;
            this.maxLogFiles = 30;
        }
        
        // Ensure logs folder exists
        if (!logsFolder.exists()) {
            logsFolder.mkdirs();
        }
        
        // Initialize current log file
        updateCurrentLogFile();
        
        // Clean old logs on startup
        cleanOldLogs();
    }
    
    /**
     * Log a rank change event.
     */
    public void logRankChange(String playerName, String playerUUID, String oldRank, 
                             String newRank, String sender, String reason, LogLevel level) {
        lock.lock();
        try {
            updateCurrentLogFile();
            
            File logFile = new File(logsFolder, currentLogFile);
            try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
                String timestamp = logDateFormat.format(new Date());
                String logEntry = String.format("[%s] [%s] Rank Change: %s (%s) %s -> %s by %s%s",
                    timestamp,
                    level.name(),
                    playerName,
                    playerUUID,
                    oldRank,
                    newRank,
                    sender,
                    reason != null && !reason.isEmpty() ? " (Reason: " + reason + ")" : ""
                );
                writer.println(logEntry);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to write to log file: " + e.getMessage());
            }
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Log a general message.
     */
    public void log(String message, LogLevel level) {
        lock.lock();
        try {
            updateCurrentLogFile();
            
            File logFile = new File(logsFolder, currentLogFile);
            try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
                String timestamp = logDateFormat.format(new Date());
                String logEntry = String.format("[%s] [%s] %s", timestamp, level.name(), message);
                writer.println(logEntry);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to write to log file: " + e.getMessage());
            }
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Update current log file based on rotation type.
     */
    private void updateCurrentLogFile() {
        String newLogFile;
        Date now = new Date();
        
        switch (rotationType) {
            case DAILY:
                newLogFile = "rank-changes-" + dateFormat.format(now) + ".log";
                break;
            case WEEKLY:
                // Get week number
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.setTime(now);
                int week = cal.get(java.util.Calendar.WEEK_OF_YEAR);
                int year = cal.get(java.util.Calendar.YEAR);
                newLogFile = String.format("rank-changes-%d-W%02d.log", year, week);
                break;
            case MONTHLY:
                SimpleDateFormat monthFormat = new SimpleDateFormat("yyyy-MM");
                newLogFile = "rank-changes-" + monthFormat.format(now) + ".log";
                break;
            case NONE:
            default:
                newLogFile = "rank-changes.log";
                break;
        }
        
        if (!newLogFile.equals(currentLogFile)) {
            currentLogFile = newLogFile;
        }
    }
    
    /**
     * Clean old log files.
     */
    private void cleanOldLogs() {
        if (maxLogFiles <= 0) return; // No limit
        
        File[] logFiles = logsFolder.listFiles((dir, name) -> name.startsWith("rank-changes-") && name.endsWith(".log"));
        if (logFiles == null || logFiles.length <= maxLogFiles) {
            return;
        }
        
        // Sort by last modified (oldest first)
        java.util.Arrays.sort(logFiles, (f1, f2) -> Long.compare(f1.lastModified(), f2.lastModified()));
        
        // Delete oldest files
        int filesToDelete = logFiles.length - maxLogFiles;
        for (int i = 0; i < filesToDelete; i++) {
            if (logFiles[i].delete()) {
                plugin.getLogger().info("Deleted old log file: " + logFiles[i].getName());
            }
        }
    }
    
    /**
     * Get log file for a specific date.
     */
    public File getLogFile(String date) {
        String filename;
        if (rotationType == LogRotationType.DAILY) {
            filename = "rank-changes-" + date + ".log";
        } else if (rotationType == LogRotationType.WEEKLY) {
            filename = "rank-changes-" + date + ".log";
        } else if (rotationType == LogRotationType.MONTHLY) {
            filename = "rank-changes-" + date + ".log";
        } else {
            filename = "rank-changes.log";
        }
        return new File(logsFolder, filename);
    }
    
    /**
     * Get all log files.
     */
    public File[] getAllLogFiles() {
        return logsFolder.listFiles((dir, name) -> name.startsWith("rank-changes-") && name.endsWith(".log"));
    }
    
    /**
     * Export logs to a file.
     */
    public boolean exportLogs(File exportFile, String startDate, String endDate) {
        try {
            File[] logFiles = getAllLogFiles();
            if (logFiles == null || logFiles.length == 0) {
                return false;
            }
            
            try (PrintWriter writer = new PrintWriter(new FileWriter(exportFile))) {
                writer.println("# ExcrelePerms Log Export");
                writer.println("# Generated: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
                writer.println();
                
                for (File logFile : logFiles) {
                    if (logFile.getName().equals(currentLogFile)) continue; // Skip current
                    
                    String fileDate = extractDateFromFilename(logFile.getName());
                    if (fileDate != null) {
                        if (startDate != null && fileDate.compareTo(startDate) < 0) continue;
                        if (endDate != null && fileDate.compareTo(endDate) > 0) continue;
                    }
                    
                    writer.println("# Log file: " + logFile.getName());
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.FileReader(logFile))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            writer.println(line);
                        }
                    }
                    writer.println();
                }
            }
            
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to export logs: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Extract date from log filename.
     */
    private String extractDateFromFilename(String filename) {
        // Format: rank-changes-YYYY-MM-DD.log
        if (filename.startsWith("rank-changes-") && filename.endsWith(".log")) {
            String datePart = filename.substring("rank-changes-".length(), filename.length() - ".log".length());
            if (datePart.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return datePart;
            }
        }
        return null;
    }
    
    public File getLogsFolder() {
        return logsFolder;
    }
}

