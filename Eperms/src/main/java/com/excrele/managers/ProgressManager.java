package com.excrele.managers;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

/**
 * Manages progress indicators for long operations.
 */
public class ProgressManager {
    
    /**
     * Display a progress bar.
     */
    public static void showProgress(CommandSender sender, String operation, int current, int total) {
        if (total == 0) return;
        
        int percentage = (int) ((current * 100.0) / total);
        int filled = (int) ((current * 20.0) / total);
        int empty = 20 - filled;
        
        StringBuilder bar = new StringBuilder();
        bar.append(ChatColor.GREEN);
        for (int i = 0; i < filled; i++) {
            bar.append("█");
        }
        bar.append(ChatColor.GRAY);
        for (int i = 0; i < empty; i++) {
            bar.append("█");
        }
        
        sender.sendMessage(ChatColor.YELLOW + operation + ": " + bar.toString() + 
                          ChatColor.WHITE + " " + percentage + "% (" + current + "/" + total + ")");
    }
    
    /**
     * Display status update.
     */
    public static void showStatus(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.GRAY + "[" + ChatColor.YELLOW + "Status" + ChatColor.GRAY + "] " + 
                          ChatColor.WHITE + message);
    }
    
    /**
     * Display completion message.
     */
    public static void showComplete(CommandSender sender, String operation, int total) {
        sender.sendMessage(ChatColor.GREEN + "✓ " + operation + " completed! (" + total + " items)");
    }
    
    /**
     * Display error in progress.
     */
    public static void showError(CommandSender sender, String operation, String error) {
        sender.sendMessage(ChatColor.RED + "✗ " + operation + " failed: " + error);
    }
}

