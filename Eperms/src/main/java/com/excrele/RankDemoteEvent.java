package com.excrele;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.command.CommandSender;

public class RankDemoteEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String oldRank;
    private final String newRank;
    private final CommandSender sender;

    public RankDemoteEvent(Player player, String oldRank, String newRank, CommandSender sender) {
        this.player = player;
        this.oldRank = oldRank;
        this.newRank = newRank;
        this.sender = sender;
    }

    public Player getPlayer() {
        return player;
    }

    public String getOldRank() {
        return oldRank;
    }

    public String getNewRank() {
        return newRank;
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