package com.excrele.events;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Called after a rank has been assigned to a player.
 * This event cannot be cancelled.
 */
public class RankPostAddEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    
    private final OfflinePlayer player;
    private final String oldRank;
    private final String newRank;
    private final String executor;
    private final String reason;
    private final long timestamp;
    
    public RankPostAddEvent(OfflinePlayer player, String oldRank, String newRank, String executor, String reason) {
        this.player = player;
        this.oldRank = oldRank;
        this.newRank = newRank;
        this.executor = executor;
        this.reason = reason;
        this.timestamp = System.currentTimeMillis();
    }
    
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }
    
    public static HandlerList getHandlerList() {
        return handlers;
    }
    
    public OfflinePlayer getPlayer() {
        return player;
    }
    
    public String getOldRank() {
        return oldRank;
    }
    
    public String getNewRank() {
        return newRank;
    }
    
    public String getExecutor() {
        return executor;
    }
    
    public String getReason() {
        return reason;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
}

