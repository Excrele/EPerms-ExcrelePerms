package com.excrele.gui;

import com.excrele.ExcrelePerms;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI for rank management using native Bukkit inventory API.
 * No external dependencies required.
 */
public class RankGUI implements Listener {
    private final ExcrelePerms plugin;
    private static final String GUI_TITLE = ChatColor.GOLD + "Rank Management";
    private static final String RANK_LIST_TITLE = ChatColor.GOLD + "Available Ranks";
    
    public RankGUI(ExcrelePerms plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    /**
     * Open main rank management GUI.
     */
    public void openMainGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, GUI_TITLE);
        
        // Main menu items
        gui.setItem(10, createItem(Material.BOOK, ChatColor.GREEN + "View Ranks", 
            "Click to view all available ranks"));
        gui.setItem(12, createItem(Material.EMERALD, ChatColor.YELLOW + "Your Rank", 
            "Click to view your current rank"));
        gui.setItem(14, createItem(Material.ANVIL, ChatColor.BLUE + "Rank Info", 
            "Click to view detailed rank information"));
        gui.setItem(16, createItem(Material.GOLD_INGOT, ChatColor.GOLD + "Buy Rank", 
            "Click to purchase a rank"));
        
        player.openInventory(gui);
    }
    
    /**
     * Open rank list GUI.
     */
    public void openRankListGUI(Player player) {
        FileConfiguration ranksConfig = plugin.getRanksConfig();
        List<String> ranks = new ArrayList<>();
        
        if (ranksConfig.contains("ranks")) {
            ranks.addAll(ranksConfig.getConfigurationSection("ranks").getKeys(false));
        }
        
        int size = Math.max(9, ((ranks.size() + 8) / 9) * 9); // Round up to multiple of 9
        size = Math.min(size, 54); // Max 6 rows
        
        Inventory gui = Bukkit.createInventory(null, size, RANK_LIST_TITLE);
        
        for (int i = 0; i < ranks.size() && i < 54; i++) {
            String rankName = ranks.get(i);
            String prefix = ranksConfig.getString("ranks." + rankName + ".info.prefix", "");
            int priority = ranksConfig.getInt("ranks." + rankName + ".priority", 0);
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Priority: " + priority);
            if (!prefix.isEmpty()) {
                lore.add(ChatColor.GRAY + "Prefix: " + ChatColor.translateAlternateColorCodes('&', prefix));
            }
            lore.add("");
            lore.add(ChatColor.YELLOW + "Click to view details");
            
            gui.setItem(i, createItem(Material.NAME_TAG, 
                ChatColor.translateAlternateColorCodes('&', prefix + rankName), lore));
        }
        
        // Add back button
        gui.setItem(size - 1, createItem(Material.BARRIER, ChatColor.RED + "Back", 
            "Click to return to main menu"));
        
        player.openInventory(gui);
    }
    
    /**
     * Open rank info GUI for a specific rank.
     */
    public void openRankInfoGUI(Player player, String rankName) {
        FileConfiguration ranksConfig = plugin.getRanksConfig();
        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.GOLD + "Rank: " + rankName);
        
        String prefix = ranksConfig.getString("ranks." + rankName + ".info.prefix", "");
        String suffix = ranksConfig.getString("ranks." + rankName + ".info.suffix", "");
        int priority = ranksConfig.getInt("ranks." + rankName + ".priority", 0);
        List<String> permissions = ranksConfig.getStringList("ranks." + rankName + ".permissions");
        List<String> inheritance = ranksConfig.getStringList("ranks." + rankName + ".inheritance");
        
        // Rank display
        gui.setItem(4, createItem(Material.NAME_TAG, 
            ChatColor.translateAlternateColorCodes('&', prefix + rankName + suffix),
            ChatColor.GRAY + "Rank Display"));
        
        // Prefix
        gui.setItem(9, createItem(Material.PAPER, ChatColor.YELLOW + "Prefix", 
            prefix.isEmpty() ? ChatColor.GRAY + "None" : ChatColor.WHITE + prefix));
        
        // Suffix
        gui.setItem(11, createItem(Material.PAPER, ChatColor.YELLOW + "Suffix", 
            suffix.isEmpty() ? ChatColor.GRAY + "None" : ChatColor.WHITE + suffix));
        
        // Priority
        gui.setItem(13, createItem(Material.GOLD_INGOT, ChatColor.YELLOW + "Priority", 
            ChatColor.WHITE + String.valueOf(priority)));
        
        // Permissions count
        gui.setItem(15, createItem(Material.BOOK, ChatColor.YELLOW + "Permissions", 
            ChatColor.WHITE + String.valueOf(permissions.size()) + " permission(s)"));
        
        // Inheritance
        gui.setItem(17, createItem(Material.CHAIN, ChatColor.YELLOW + "Inheritance", 
            inheritance.isEmpty() ? ChatColor.GRAY + "None" : 
            ChatColor.WHITE + String.join(", ", inheritance)));
        
        // Back button
        gui.setItem(22, createItem(Material.BARRIER, ChatColor.RED + "Back", 
            "Click to return"));
        
        player.openInventory(gui);
    }
    
    /**
     * Open rank purchase GUI.
     */
    public void openRankPurchaseGUI(Player player) {
        FileConfiguration ranksConfig = plugin.getRanksConfig();
        List<String> purchasableRanks = new ArrayList<>();
        
        // Find ranks with prices
        if (ranksConfig.contains("ranks")) {
            for (String rankName : ranksConfig.getConfigurationSection("ranks").getKeys(false)) {
                if (ranksConfig.contains("ranks." + rankName + ".price")) {
                    purchasableRanks.add(rankName);
                }
            }
        }
        
        int size = Math.max(9, ((purchasableRanks.size() + 8) / 9) * 9);
        size = Math.min(size, 54);
        
        Inventory gui = Bukkit.createInventory(null, size, ChatColor.GOLD + "Purchase Ranks");
        
        for (int i = 0; i < purchasableRanks.size() && i < 54; i++) {
            String rankName = purchasableRanks.get(i);
            double price = ranksConfig.getDouble("ranks." + rankName + ".price", 0.0);
            String prefix = ranksConfig.getString("ranks." + rankName + ".info.prefix", "");
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GOLD + "Price: " + price);
            lore.add("");
            lore.add(ChatColor.YELLOW + "Click to purchase");
            
            gui.setItem(i, createItem(Material.EMERALD, 
                ChatColor.translateAlternateColorCodes('&', prefix + rankName), lore));
        }
        
        if (purchasableRanks.isEmpty()) {
            gui.setItem(13, createItem(Material.BARRIER, ChatColor.RED + "No Ranks Available", 
                ChatColor.GRAY + "No ranks are available for purchase"));
        }
        
        // Back button
        gui.setItem(size - 1, createItem(Material.BARRIER, ChatColor.RED + "Back", 
            "Click to return"));
        
        player.openInventory(gui);
    }
    
    /**
     * Create an ItemStack with display name and lore.
     */
    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> loreList = new ArrayList<>();
            for (String line : lore) {
                loreList.add(line);
            }
            meta.setLore(loreList);
            item.setItemMeta(meta);
        }
        return item;
    }
    
    /**
     * Create an ItemStack with display name and lore list.
     */
    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();
        
        if (title.equals(GUI_TITLE)) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) {
                return;
            }
            
            switch (event.getSlot()) {
                case 10: // View Ranks
                    openRankListGUI(player);
                    break;
                case 12: // Your Rank
                    String currentRank = plugin.getPlayerRank(player.getUniqueId());
                    player.sendMessage(ChatColor.GOLD + "Your current rank: " + ChatColor.WHITE + currentRank);
                    player.closeInventory();
                    break;
                case 14: // Rank Info
                    player.sendMessage(ChatColor.YELLOW + "Use /rank info <rank> for detailed information");
                    player.closeInventory();
                    break;
                case 16: // Buy Rank
                    openRankPurchaseGUI(player);
                    break;
            }
        } else if (title.equals(RANK_LIST_TITLE)) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) {
                return;
            }
            
            if (clicked.getType() == Material.BARRIER) {
                openMainGUI(player);
                return;
            }
            
            if (clicked.hasItemMeta() && clicked.getItemMeta().hasDisplayName()) {
                String rankName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
                openRankInfoGUI(player, rankName);
            }
        } else if (title.startsWith(ChatColor.GOLD + "Rank: ")) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) {
                return;
            }
            
            if (clicked.getType() == Material.BARRIER) {
                openRankListGUI(player);
            }
        } else if (title.equals(ChatColor.GOLD + "Purchase Ranks")) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) {
                return;
            }
            
            if (clicked.getType() == Material.BARRIER) {
                openMainGUI(player);
                return;
            }
            
            if (clicked.hasItemMeta() && clicked.getItemMeta().hasDisplayName()) {
                String rankName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
                // Handle rank purchase
                plugin.handleRankPurchase(player, rankName);
            }
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // Can add cleanup logic here if needed
    }
}

