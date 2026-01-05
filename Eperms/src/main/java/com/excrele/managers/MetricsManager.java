package com.excrele.managers;

import com.excrele.ExcrelePerms;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks plugin usage and performance metrics.
 * Standalone implementation - no external dependencies.
 */
public class MetricsManager {
    private final ExcrelePerms plugin;
    private final Map<String, AtomicLong> counters = new HashMap<>();
    private final Map<String, Long> timings = new HashMap<>();
    private long startTime;
    
    public MetricsManager(ExcrelePerms plugin) {
        this.plugin = plugin;
        this.startTime = System.currentTimeMillis();
        initializeCounters();
    }
    
    private void initializeCounters() {
        counters.put("rank_changes", new AtomicLong(0));
        counters.put("commands_executed", new AtomicLong(0));
        counters.put("players_managed", new AtomicLong(0));
        counters.put("errors", new AtomicLong(0));
    }
    
    /**
     * Increment a counter.
     */
    public void incrementCounter(String counter) {
        counters.computeIfAbsent(counter, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    /**
     * Record a timing.
     */
    public void recordTiming(String operation, long milliseconds) {
        timings.put(operation, milliseconds);
    }
    
    /**
     * Get counter value.
     */
    public long getCounter(String counter) {
        AtomicLong value = counters.get(counter);
        return value != null ? value.get() : 0;
    }
    
    /**
     * Get all metrics.
     */
    public Map<String, Object> getAllMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("uptime_seconds", (System.currentTimeMillis() - startTime) / 1000);
        metrics.put("counters", getCounters());
        metrics.put("timings", new HashMap<>(timings));
        return metrics;
    }
    
    /**
     * Get all counters.
     */
    public Map<String, Long> getCounters() {
        Map<String, Long> result = new HashMap<>();
        counters.forEach((key, value) -> result.put(key, value.get()));
        return result;
    }
    
    /**
     * Save metrics to file.
     */
    public void saveMetrics() {
        FileConfiguration config = plugin.getRanksConfig();
        config.set("metrics", getAllMetrics());
        // Save would be handled by YAMLFileManager
    }
}

