package com.excrele;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.command.CommandSender;

public class RankAddEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String newRank;
    private final String oldRank;
    private final CommandSender sender;

    public RankAddEvent(Player player, String newRank, String oldRank, CommandSender sender) {
        this.player = player;
        this.newRank = newRank;
        this.oldRank = oldRank;
        this.sender = sender;
    }

    public Player getPlayer() {
        return player;
    }

    public String getNewRank() {
        return newRank;
    }

    public String getOldRank() {
        return oldRank;
    }

    public CommandSender getSender() {
        return sender;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}