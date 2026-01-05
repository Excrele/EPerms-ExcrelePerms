package com.excrele.api;

import com.excrele.ExcrelePerms;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Public API for ExcrelePerms plugin.
 * Provides methods for other plugins to interact with the rank system.
 */
public class ExcrelePermsAPI {
    private static ExcrelePerms plugin;

    /**
     * Initialize the API with the plugin instance.
     * This is called automatically by ExcrelePerms on enable.
     *
     * @param instance The ExcrelePerms plugin instance
     */
    public static void initialize(ExcrelePerms instance) {
        plugin = instance;
    }

    /**
     * Get the plugin instance.
     *
     * @return The ExcrelePerms plugin instance, or null if not initialized
     */
    public static ExcrelePerms getPlugin() {
        return plugin;
    }

    /**
     * Check if the API is initialized.
     *
     * @return true if the API is initialized, false otherwise
     */
    public static boolean isInitialized() {
        return plugin != null && plugin.isEnabled();
    }

    /**
     * Get a player's current rank.
     *
     * @param player The player
     * @return The player's rank name, or "default" if not set
     */
    public static String getPlayerRank(Player player) {
        if (!isInitialized()) return "default";
        return plugin.getPlayerRank(player.getUniqueId());
    }

    /**
     * Get a player's current rank by UUID.
     *
     * @param uuid The player's UUID
     * @return The player's rank name, or "default" if not set
     */
    public static String getPlayerRank(UUID uuid) {
        if (!isInitialized()) return "default";
        return plugin.getPlayerRank(uuid);
    }

    /**
     * Get a player's current rank by offline player.
     *
     * @param offlinePlayer The offline player
     * @return The player's rank name, or "default" if not set
     */
    public static String getPlayerRank(OfflinePlayer offlinePlayer) {
        if (!isInitialized()) return "default";
        return plugin.getPlayerRank(offlinePlayer.getUniqueId());
    }

    /**
     * Set a player's rank.
     *
     * @param player The player
     * @param rank   The rank name
     * @return true if successful, false otherwise
     */
    public static boolean setPlayerRank(Player player, String rank) {
        if (!isInitialized()) return false;
        return plugin.setPlayerRank(player.getUniqueId(), rank);
    }

    /**
     * Set a player's rank by UUID.
     *
     * @param uuid The player's UUID
     * @param rank The rank name
     * @return true if successful, false otherwise
     */
    public static boolean setPlayerRank(UUID uuid, String rank) {
        if (!isInitialized()) return false;
        return plugin.setPlayerRank(uuid, rank);
    }

    /**
     * Check if a rank exists.
     *
     * @param rank The rank name
     * @return true if the rank exists, false otherwise
     */
    public static boolean rankExists(String rank) {
        if (!isInitialized()) return false;
        return plugin.rankExists(rank);
    }

    /**
     * Get the prefix for a rank.
     *
     * @param rank The rank name
     * @return The rank prefix, or empty string if not set
     */
    public static String getRankPrefix(String rank) {
        if (!isInitialized()) return "";
        return plugin.getRankPrefix(rank);
    }

    /**
     * Get the suffix for a rank.
     *
     * @param rank The rank name
     * @return The rank suffix, or empty string if not set
     */
    public static String getRankSuffix(String rank) {
        if (!isInitialized()) return "";
        return plugin.getRankSuffix(rank);
    }

    /**
     * Get all permissions for a rank.
     *
     * @param rank The rank name
     * @return List of permissions, or empty list if rank doesn't exist
     */
    public static List<String> getRankPermissions(String rank) {
        if (!isInitialized()) return java.util.Collections.emptyList();
        return plugin.getRankPermissions(rank);
    }

    /**
     * Get all ranks that a rank inherits from.
     *
     * @param rank The rank name
     * @return List of inherited rank names, or empty list if none
     */
    public static List<String> getRankInheritance(String rank) {
        if (!isInitialized()) return java.util.Collections.emptyList();
        return plugin.getRankInheritance(rank);
    }

    /**
     * Get all available ranks.
     *
     * @return List of all rank names, or empty list if none
     */
    public static List<String> getAllRanks() {
        if (!isInitialized()) return java.util.Collections.emptyList();
        return plugin.getAllRanks();
    }

    /**
     * Reload the ranks configuration.
     *
     * @return true if successful, false otherwise
     */
    public static boolean reloadConfig() {
        if (!isInitialized()) return false;
        return plugin.reloadRanksConfig();
    }
}

