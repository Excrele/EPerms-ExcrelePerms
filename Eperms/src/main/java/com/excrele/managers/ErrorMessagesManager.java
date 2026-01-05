package com.excrele.managers;

import org.bukkit.ChatColor;

/**
 * Manages contextual error messages with suggestions.
 */
public class ErrorMessagesManager {
    
    public static String getRankNotFoundError(String rank) {
        return ChatColor.RED + "Rank '" + rank + "' does not exist!" +
               ChatColor.YELLOW + "\nSuggestion: Use /rank list to see all available ranks.";
    }
    
    public static String getPlayerNotFoundError(String playerName) {
        return ChatColor.RED + "Player '" + playerName + "' not found!" +
               ChatColor.YELLOW + "\nSuggestion: Make sure the player has joined the server before, or use their UUID.";
    }
    
    public static String getPermissionDeniedError(String permission) {
        return ChatColor.RED + "You don't have permission to use this command!" +
               ChatColor.YELLOW + "\nRequired permission: " + permission;
    }
    
    public static String getInvalidArgumentError(String argument, String expected) {
        return ChatColor.RED + "Invalid argument: '" + argument + "'" +
               ChatColor.YELLOW + "\nExpected: " + expected;
    }
    
    public static String getFileError(String fileName, String operation) {
        return ChatColor.RED + "Error " + operation + " file: " + fileName +
               ChatColor.YELLOW + "\nSuggestion: Check file permissions and disk space.";
    }
    
    public static String getYAMLParseError(String fileName, int line) {
        return ChatColor.RED + "YAML parse error in " + fileName + " at line " + line +
               ChatColor.YELLOW + "\nSuggestion: Check YAML syntax, especially indentation and quotes.";
    }
}

