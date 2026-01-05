package com.excrele.managers;

import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches calculated permissions for better performance.
 */
public class PermissionCacheManager {
    private final Map<UUID, CachedPermissions> permissionCache = new ConcurrentHashMap<>();
    private final long cacheTTL;
    
    private static class CachedPermissions {
        final Set<String> permissions;
        final String rank;
        final long timestamp;
        
        CachedPermissions(Set<String> permissions, String rank) {
            this.permissions = permissions;
            this.rank = rank;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired(long ttl) {
            return System.currentTimeMillis() - timestamp > ttl;
        }
    }
    
    public PermissionCacheManager(long cacheTTL) {
        this.cacheTTL = cacheTTL;
    }
    
    /**
     * Get cached permissions for a player.
     */
    public Set<String> getCachedPermissions(Player player, String rank) {
        UUID uuid = player.getUniqueId();
        CachedPermissions cached = permissionCache.get(uuid);
        
        if (cached != null && !cached.isExpired(cacheTTL) && cached.rank.equals(rank)) {
            return cached.permissions;
        }
        
        return null;
    }
    
    /**
     * Cache permissions for a player.
     */
    public void cachePermissions(Player player, String rank, Set<String> permissions) {
        UUID uuid = player.getUniqueId();
        permissionCache.put(uuid, new CachedPermissions(permissions, rank));
    }
    
    /**
     * Invalidate cache for a player.
     */
    public void invalidateCache(UUID uuid) {
        permissionCache.remove(uuid);
    }
    
    /**
     * Invalidate all caches.
     */
    public void clearCache() {
        permissionCache.clear();
    }
    
    /**
     * Clean expired entries.
     */
    public void cleanExpired() {
        permissionCache.entrySet().removeIf(entry -> entry.getValue().isExpired(cacheTTL));
    }
    
    /**
     * Get cache statistics.
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cached_players", permissionCache.size());
        stats.put("cache_ttl_ms", cacheTTL);
        return stats;
    }
}

