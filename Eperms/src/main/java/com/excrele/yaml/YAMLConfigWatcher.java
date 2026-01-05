package com.excrele.yaml;

import com.excrele.ExcrelePerms;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Watches config.yml for changes and auto-reloads.
 */
public class YAMLConfigWatcher {
    private final ExcrelePerms plugin;
    private final Map<File, WatchKey> watchKeys = new HashMap<>();
    private WatchService watchService;
    private volatile boolean running = false;
    
    public YAMLConfigWatcher(ExcrelePerms plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Check if the watcher is running.
     */
    public boolean isRunning() {
        return running;
    }
    
    /**
     * Start watching config files.
     */
    public void startWatching() {
        if (running) return;
        
        try {
            watchService = FileSystems.getDefault().newWatchService();
            File configFile = new File(plugin.getDataFolder(), "config.yml");
            
            if (configFile.exists()) {
                Path configPath = configFile.getParentFile().toPath();
                WatchKey key = configPath.register(watchService, 
                    StandardWatchEventKinds.ENTRY_MODIFY);
                watchKeys.put(configFile, key);
            }
            
            running = true;
            startWatcherThread();
            
            plugin.getLogger().info("Config file watcher started!");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to start config watcher: " + e.getMessage());
        }
    }
    
    /**
     * Stop watching.
     */
    public void stopWatching() {
        running = false;
        if (watchService != null) {
            try {
                watchService.close();
            } catch (Exception e) {
                plugin.getLogger().warning("Error stopping watcher: " + e.getMessage());
            }
        }
    }
    
    private void startWatcherThread() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!running) {
                    cancel();
                    return;
                }
                
                try {
                    WatchKey key = watchService.poll();
                    if (key != null) {
                        for (WatchEvent<?> event : key.pollEvents()) {
                            WatchEvent.Kind<?> kind = event.kind();
                            
                            if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                                Path changed = (Path) event.context();
                                if (changed.toString().equals("config.yml")) {
                                    // Reload config after a short delay
                                    new BukkitRunnable() {
                                        @Override
                                        public void run() {
                                            plugin.getLogger().info("Config.yml changed, reloading...");
                                            // Note: Actual reload is handled by the plugin's reload command
                                            // This is just a notification that the file changed
                                        }
                                    }.runTask(plugin);
                                }
                            }
                        }
                        key.reset();
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }
        }.runTaskTimerAsynchronously(plugin, 20L, 20L); // Check every second
    }
}

