package com.excrele.events;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Called before a rank is assigned to a player.
 * This event can be cancelled to prevent the rank change.
 */
public class RankPreAddEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled = false;
    
    private final OfflinePlayer player;
    private final String oldRank;
    private final String newRank;
    private final String executor;
    private final String reason;
    
    public RankPreAddEvent(OfflinePlayer player, String oldRank, String newRank, String executor, String reason) {
        this.player = player;
        this.oldRank = oldRank;
        this.newRank = newRank;
        this.executor = executor;
        this.reason = reason;
    }
    
    @Override
    public boolean isCancelled() {
        return cancelled;
    }
    
    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
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
}

