package com.example.ecore;

import com.excrele.RankAddEvent;
import com.excrele.RankPromoteEvent;
import com.excrele.RankDemoteEvent;
import com.excrele.RankReloadEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class ExcrelePermsListener implements Listener {
    private final JavaPlugin plugin;

    public ExcrelePermsListener(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onRankAdd(RankAddEvent event) {
        plugin.getLogger().info(String.format(
                "[ExcrelePerms] Player %s (UUID: %s) had rank changed from %s to %s by %s",
                event.getPlayer().getName(),
                event.getPlayer().getUniqueId(),
                event.getOldRank(),
                event.getNewRank(),
                event.getSender().getName()
        ));
        // Add your logging logic here, e.g., save to a file or database
    }

    @EventHandler
    public void onRankPromote(RankPromoteEvent event) {
        plugin.getLogger().info(String.format(
                "[ExcrelePerms] Player %s (UUID: %s) promoted from %s to %s by %s",
                event.getPlayer().getName(),
                event.getPlayer().getUniqueId(),
                event.getOldRank(),
                event.getNewRank(),
                event.getSender().getName()
        ));
        // Add your logging logic here
    }

    @EventHandler
    public void onRankDemote(RankDemoteEvent event) {
        plugin.getLogger().info(String.format(
                "[ExcrelePerms] Player %s (UUID: %s) demoted from %s to %s by %s",
                event.getPlayer().getName(),
                event.getPlayer().getUniqueId(),
                event.getOldRank(),
                event.getNewRank(),
                event.getSender().getName()
        ));
        // Add your logging logic here
    }

    @EventHandler
    public void onRankReload(RankReloadEvent event) {
        plugin.getLogger().info(String.format(
                "[ExcrelePerms] Ranks configuration reloaded by %s",
                event.getSender().getName()
        ));
        // Add your logging logic here
    }
}