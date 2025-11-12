package com.excrele;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.command.CommandSender;

public class RankReloadEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final CommandSender sender;

    public RankReloadEvent(CommandSender sender) {
        this.sender = sender;
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