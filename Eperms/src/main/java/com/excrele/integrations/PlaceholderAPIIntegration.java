package com.excrele.integrations;

import com.excrele.ExcrelePerms;
import com.excrele.managers.TrackManager;
import org.bukkit.OfflinePlayer;

/**
 * PlaceholderAPI integration for ExcrelePerms.
 * Note: This class will only work if PlaceholderAPI is installed.
 * The actual PlaceholderExpansion registration is handled conditionally in ExcrelePerms.
 */
public class PlaceholderAPIIntegration {
    private final ExcrelePerms plugin;
    private final TrackManager trackManager;
    
    public PlaceholderAPIIntegration(ExcrelePerms plugin, TrackManager trackManager) {
        this.plugin = plugin;
        this.trackManager = trackManager;
    }
    
    /**
     * Get placeholder value for a player.
     * This method is called by PlaceholderAPI if it's installed.
     */
    public String getPlaceholder(OfflinePlayer player, String identifier) {
        if (player == null) {
            return "";
        }
        
        String rank = plugin.getPlayerRank(player.getUniqueId());
        
        switch (identifier.toLowerCase()) {
            case "rank":
                return rank;
                
            case "rank_prefix":
                return plugin.getRankPrefix(rank);
                
            case "rank_suffix":
                return plugin.getRankSuffix(rank);
                
            case "rank_display":
                String prefix = plugin.getRankPrefix(rank);
                String suffix = plugin.getRankSuffix(rank);
                String name = player.getName() != null ? player.getName() : "";
                return prefix + name + suffix;
                
            case "rank_priority":
                return String.valueOf(plugin.getRanksConfig().getInt("ranks." + rank + ".priority", 0));
                
            case "next_rank":
                String trackName = trackManager.getRankTrack(rank);
                if (trackName == null || trackName.isEmpty()) {
                    trackName = "defaultTrack";
                }
                String nextRank = trackManager.getNextRank(rank, trackName);
                return nextRank != null ? nextRank : "";
                
            case "previous_rank":
                trackName = trackManager.getRankTrack(rank);
                if (trackName == null || trackName.isEmpty()) {
                    trackName = "defaultTrack";
                }
                String prevRank = trackManager.getPreviousRank(rank, trackName);
                return prevRank != null ? prevRank : "";
                
            default:
                return null;
        }
    }
}

