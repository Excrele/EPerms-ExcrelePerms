package com.excrele.integrations;

import com.excrele.ExcrelePerms;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;

/**
 * Vault integration for ExcrelePerms.
 * Works standalone if Vault is not available, with optional Vault support if installed.
 * Uses reflection to avoid compile-time dependencies on Vault.
 */
public class VaultIntegration {
    private final ExcrelePerms plugin;
    private Object economy = null;
    private Object permission = null;
    private boolean vaultAvailable = false;
    private Class<?> economyClass = null;
    private Class<?> permissionClass = null;
    
    public VaultIntegration(ExcrelePerms plugin) {
        this.plugin = plugin;
        setupVault();
    }
    
    /**
     * Setup Vault integration if available (using reflection to avoid dependencies).
     */
    private void setupVault() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().info("Vault not found. Using standalone economy mode.");
            return;
        }
        
        try {
            // Use reflection to load Vault classes
            economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            permissionClass = Class.forName("net.milkbowl.vault.permission.Permission");
            
            RegisteredServiceProvider<?> economyProvider = 
                plugin.getServer().getServicesManager().getRegistration(economyClass);
            if (economyProvider != null) {
                economy = economyProvider.getProvider();
                plugin.getLogger().info("Vault Economy integration enabled!");
            }
            
            RegisteredServiceProvider<?> permissionProvider = 
                plugin.getServer().getServicesManager().getRegistration(permissionClass);
            if (permissionProvider != null) {
                permission = permissionProvider.getProvider();
                plugin.getLogger().info("Vault Permission integration enabled!");
            }
            
            vaultAvailable = (economy != null || permission != null);
        } catch (ClassNotFoundException e) {
            plugin.getLogger().info("Vault classes not found. Using standalone mode.");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to setup Vault: " + e.getMessage());
        }
    }
    
    /**
     * Check if Vault is available.
     */
    public boolean isVaultAvailable() {
        return vaultAvailable;
    }
    
    /**
     * Check if economy is available.
     */
    public boolean isEconomyAvailable() {
        return economy != null;
    }
    
    /**
     * Get player's balance (works with or without Vault).
     */
    public double getBalance(OfflinePlayer player) {
        if (economy != null) {
            try {
                Method getBalanceMethod = economyClass.getMethod("getBalance", OfflinePlayer.class);
                Object result = getBalanceMethod.invoke(economy, player);
                return result != null ? ((Number) result).doubleValue() : 0.0;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to get balance from Vault: " + e.getMessage());
            }
        }
        // Standalone mode - use internal economy
        return getInternalBalance(player);
    }
    
    /**
     * Withdraw money from player (works with or without Vault).
     */
    public boolean withdrawPlayer(OfflinePlayer player, double amount) {
        if (economy != null) {
            try {
                Method withdrawMethod = economyClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
                Object result = withdrawMethod.invoke(economy, player, amount);
                if (result != null) {
                    Method transactionSuccessMethod = result.getClass().getMethod("transactionSuccess");
                    Object success = transactionSuccessMethod.invoke(result);
                    return success != null && (Boolean) success;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to withdraw from Vault: " + e.getMessage());
            }
        }
        // Standalone mode - use internal economy
        return withdrawInternal(player, amount);
    }
    
    /**
     * Check if player has permission (works with or without Vault).
     */
    public boolean hasPermission(OfflinePlayer player, String perm) {
        if (this.permission != null) {
            try {
                Method hasPermissionMethod = permissionClass.getMethod("playerHas", String.class, OfflinePlayer.class, String.class);
                Object result = hasPermissionMethod.invoke(this.permission, (String) null, player, perm);
                return result != null && (Boolean) result;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to check permission with Vault: " + e.getMessage());
            }
        }
        // Standalone mode - use Bukkit permissions
        if (player.isOnline()) {
            return player.getPlayer().hasPermission(perm);
        }
        return false;
    }
    
    /**
     * Get internal balance (standalone mode).
     */
    private double getInternalBalance(OfflinePlayer player) {
        // Check if player has balance stored in config
        return plugin.getRanksConfig().getDouble("economy.players." + player.getUniqueId() + ".balance", 0.0);
    }
    
    /**
     * Withdraw from internal balance (standalone mode).
     */
    private boolean withdrawInternal(OfflinePlayer player, double amount) {
        double currentBalance = getInternalBalance(player);
        if (currentBalance >= amount) {
            plugin.getRanksConfig().set("economy.players." + player.getUniqueId() + ".balance", currentBalance - amount);
            plugin.getYAMLFileManager().saveConfigSync("ranks.yml", plugin.getRanksConfig());
            return true;
        }
        return false;
    }
    
    /**
     * Deposit to internal balance (standalone mode).
     */
    public boolean depositPlayer(OfflinePlayer player, double amount) {
        if (economy != null) {
            try {
                Method depositMethod = economyClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
                Object result = depositMethod.invoke(economy, player, amount);
                if (result != null) {
                    Method transactionSuccessMethod = result.getClass().getMethod("transactionSuccess");
                    Object success = transactionSuccessMethod.invoke(result);
                    return success != null && (Boolean) success;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to deposit to Vault: " + e.getMessage());
            }
        }
        // Standalone mode
        double currentBalance = getInternalBalance(player);
        plugin.getRanksConfig().set("economy.players." + player.getUniqueId() + ".balance", currentBalance + amount);
        plugin.getYAMLFileManager().saveConfigSync("ranks.yml", plugin.getRanksConfig());
        return true;
    }
    
    /**
     * Format currency amount.
     */
    public String format(double amount) {
        if (economy != null) {
            try {
                Method formatMethod = economyClass.getMethod("format", double.class);
                Object result = formatMethod.invoke(economy, amount);
                return result != null ? result.toString() : String.format("%.2f", amount);
            } catch (Exception e) {
                // Fall through to default formatting
            }
        }
        return String.format("%.2f", amount);
    }
    
    /**
     * Get currency name.
     */
    public String currencyName() {
        if (economy != null) {
            try {
                Method currencyNameMethod = economyClass.getMethod("currencyNameSingular");
                Object result = currencyNameMethod.invoke(economy);
                return result != null ? result.toString() : "coins";
            } catch (Exception e) {
                // Fall through to default
            }
        }
        return "coins";
    }
    
    public Object getEconomy() {
        return economy;
    }
    
    public Object getPermission() {
        return permission;
    }
}

