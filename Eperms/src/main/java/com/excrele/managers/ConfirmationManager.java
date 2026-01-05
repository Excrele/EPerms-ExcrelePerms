package com.excrele.managers;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages confirmation prompts for destructive operations.
 */
public class ConfirmationManager {
    private final Map<UUID, PendingConfirmation> pendingConfirmations = new HashMap<>();
    
    private static class PendingConfirmation {
        final String command;
        @SuppressWarnings("unused")
        final String[] args;
        final long timestamp;
        
        PendingConfirmation(String command, String[] args) {
            this.command = command;
            this.args = args;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > 30000; // 30 seconds
        }
    }
    
    /**
     * Check if a command requires confirmation.
     */
    public boolean requiresConfirmation(String command, String[] args) {
        if (args.length == 0) return false;
        
        // Destructive operations that need confirmation
        if (command.equals("delete") && args.length >= 1) {
            return !args[args.length - 1].equalsIgnoreCase("confirm");
        }
        if (command.equals("backup") && args.length >= 2 && args[0].equals("delete")) {
            return !args[args.length - 1].equalsIgnoreCase("confirm");
        }
        if (command.equals("track") && args.length >= 2 && args[0].equals("delete")) {
            return !args[args.length - 1].equalsIgnoreCase("confirm");
        }
        
        return false;
    }
    
    /**
     * Request confirmation for a command.
     */
    public void requestConfirmation(CommandSender sender, String command, String[] args) {
        if (!(sender instanceof Player)) {
            // Console doesn't need confirmation
            return;
        }
        
        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();
        
        // Clean expired confirmations
        pendingConfirmations.entrySet().removeIf(entry -> entry.getValue().isExpired());
        
        pendingConfirmations.put(uuid, new PendingConfirmation(command, args));
        
        player.sendMessage(org.bukkit.ChatColor.YELLOW + "⚠ Warning: This action cannot be undone!");
        player.sendMessage(org.bukkit.ChatColor.YELLOW + "Type the command again with 'confirm' at the end to proceed.");
        player.sendMessage(org.bukkit.ChatColor.GRAY + "Example: " + buildCommandWithConfirm(command, args));
    }
    
    /**
     * Check if player has pending confirmation.
     */
    public boolean hasPendingConfirmation(CommandSender sender, String command) {
        if (!(sender instanceof Player)) return false;
        
        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();
        PendingConfirmation confirmation = pendingConfirmations.get(uuid);
        
        if (confirmation == null || confirmation.isExpired()) {
            pendingConfirmations.remove(uuid);
            return false;
        }
        
        return confirmation.command.equals(command);
    }
    
    /**
     * Clear pending confirmation.
     */
    public void clearConfirmation(CommandSender sender) {
        if (!(sender instanceof Player)) return;
        pendingConfirmations.remove(((Player) sender).getUniqueId());
    }
    
    @SuppressWarnings("unused")
    private String getActionDescription(String command, String[] args) {
        if (command.equals("delete")) {
            return "delete rank '" + (args.length > 0 ? args[0] : "") + "'";
        }
        if (command.equals("backup") && args.length > 1 && args[0].equals("delete")) {
            return "delete backup '" + args[1] + "'";
        }
        if (command.equals("track") && args.length > 1 && args[0].equals("delete")) {
            return "delete track '" + args[1] + "'";
        }
        return command;
    }
    
    private String buildCommandWithConfirm(String command, String[] args) {
        StringBuilder sb = new StringBuilder("/rank " + command);
        for (String arg : args) {
            sb.append(" ").append(arg);
        }
        sb.append(" confirm");
        return sb.toString();
    }
}

