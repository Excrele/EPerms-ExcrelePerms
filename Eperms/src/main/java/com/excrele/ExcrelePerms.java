package com.excrele;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.ChatColor;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.OfflinePlayer;
import com.excrele.api.ExcrelePermsAPI;
import com.excrele.yaml.YAMLFileManager;
import java.util.concurrent.CompletableFuture;
import com.excrele.yaml.YAMLBackupManager;
import com.excrele.yaml.YAMLValidationManager;
import com.excrele.yaml.YAMLHistoryManager;
import com.excrele.yaml.YAMLExportImportManager;
import com.excrele.yaml.YAMLLoggingManager;
import com.excrele.yaml.YAMLCommentManager;
import com.excrele.managers.TrackManager;
import com.excrele.managers.RankManager;
import com.excrele.managers.TemporaryRankManager;

@SuppressWarnings("deprecation")
public class ExcrelePerms extends JavaPlugin implements Listener {
    private File configFile;
    private FileConfiguration ranksConfig;
    private Map<UUID, PermissionAttachment> playerPermissions;
    
    // YAML Managers
    private YAMLFileManager yamlFileManager;
    private YAMLBackupManager backupManager;
    private YAMLValidationManager validationManager;
    private YAMLHistoryManager historyManager;
    private YAMLExportImportManager exportImportManager;
    private YAMLLoggingManager loggingManager;
    private YAMLCommentManager commentManager;
    
    // Feature Managers
    private TrackManager trackManager;
    private RankManager rankManager;
    private TemporaryRankManager temporaryRankManager;
    private com.excrele.managers.BulkOperationsManager bulkOperationsManager;
    private com.excrele.integrations.VaultIntegration vaultIntegration;
    private com.excrele.gui.RankGUI rankGUI;
    private com.excrele.managers.ConfirmationManager confirmationManager;
    private com.excrele.managers.ProgressManager progressManager;
    private com.excrele.managers.PermissionCacheManager permissionCacheManager;
    private com.excrele.yaml.YAMLConfigWatcher configWatcher;
    private com.excrele.managers.MetricsManager metricsManager;
    private com.excrele.managers.MultiWorldManager multiWorldManager;
    private com.excrele.managers.MigrationManager migrationManager;

    @Override
    public void onEnable() {
        // Register as event listener
        getServer().getPluginManager().registerEvents(this, this);

        // Initialize YAML managers
        yamlFileManager = new YAMLFileManager(this);
        backupManager = new YAMLBackupManager(this, yamlFileManager);
        validationManager = new YAMLValidationManager(this, yamlFileManager);
        historyManager = new YAMLHistoryManager(this, yamlFileManager);
        exportImportManager = new YAMLExportImportManager(this, yamlFileManager);
        loggingManager = new YAMLLoggingManager(this);
        commentManager = new YAMLCommentManager(this, yamlFileManager);
        
        // Initialize feature managers
        trackManager = new TrackManager(this, yamlFileManager);
        rankManager = new RankManager(this, yamlFileManager);
        temporaryRankManager = new TemporaryRankManager(this, yamlFileManager);
        temporaryRankManager.loadTemporaryRanks();
        bulkOperationsManager = new com.excrele.managers.BulkOperationsManager(this, yamlFileManager);
        
        // Initialize integrations (optional dependencies)
        vaultIntegration = new com.excrele.integrations.VaultIntegration(this);
        
        // Initialize GUI
        rankGUI = new com.excrele.gui.RankGUI(this);
        
        // Initialize new managers
        confirmationManager = new com.excrele.managers.ConfirmationManager();
        progressManager = new com.excrele.managers.ProgressManager();
        permissionCacheManager = new com.excrele.managers.PermissionCacheManager(300000); // 5 minute TTL
        configWatcher = new com.excrele.yaml.YAMLConfigWatcher(this);
        configWatcher.startWatching();
        metricsManager = new com.excrele.managers.MetricsManager(this);
        multiWorldManager = new com.excrele.managers.MultiWorldManager(this, yamlFileManager);
        migrationManager = new com.excrele.managers.MigrationManager(this, yamlFileManager);

        // Initialize configuration (backward compatibility - use single file mode initially)
        configFile = new File(getDataFolder(), "ranks.yml");
        if (!configFile.exists()) {
            saveResource("ranks.yml", false);
        }
        ranksConfig = yamlFileManager.getConfig("ranks.yml");
        playerPermissions = new HashMap<>();

        // Validate configuration on startup
        YAMLValidationManager.ValidationResult validation = validationManager.validateAll();
        if (!validation.isValid()) {
            getLogger().warning("Configuration validation found errors:");
            for (String error : validation.getErrors()) {
                getLogger().warning("  - " + error);
            }
        }
        if (!validation.getWarnings().isEmpty()) {
            for (String warning : validation.getWarnings()) {
                getLogger().info("  - " + warning);
            }
        }

        // Load permissions for online players
        getServer().getOnlinePlayers().forEach(this::loadPlayerPermissions);
        
        // Start tab list sorting task (runs every 5 seconds)
        getServer().getScheduler().runTaskTimer(this, this::sortTabListByPriority, 100L, 100L);
        
        // Register event listener for rank events
        getServer().getPluginManager().registerEvents(new ExcrelePermsListener(this), this);
        
        // Register tab completer
        PluginCommand rankCommand = getCommand("rank");
        if (rankCommand != null) {
            rankCommand.setTabCompleter(new TabCompleter() {
                @Override
                public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
                    return ExcrelePerms.this.onTabComplete(sender, command, alias, args);
                }
            });
        }
        
        // Initialize API
        ExcrelePermsAPI.initialize(this);
        
        // Register PlaceholderAPI if available (optional dependency)
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                // PlaceholderAPI integration will be registered via reflection if available
                getLogger().info("PlaceholderAPI detected! Integration available.");
            } catch (Exception e) {
                getLogger().warning("PlaceholderAPI integration note: " + e.getMessage());
            }
        }
        
        getLogger().info("ExcrelePerms enabled!");
    }

    @Override
    public void onDisable() {
        // Remove permissions for all players
        playerPermissions.values().forEach(PermissionAttachment::remove);
        playerPermissions.clear();
        getLogger().info("ExcrelePerms disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("rank")) {
            if (args.length == 0) {
                sender.sendMessage(ChatColor.RED + "Usage: /rank <add|assign|promote|demote|reload> [args]");
                return true;
            }

            // Check base permission
            if (!sender.hasPermission("excreleperms.use")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                return true;
            }

            switch (args[0].toLowerCase()) {
                case "add":
                    if (!sender.hasPermission("excreleperms.add")) {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to add ranks!");
                        return true;
                    }
                    if (args.length < 3) {
                        sender.sendMessage(ChatColor.RED + "Usage: /rank add <player> <rank>");
                        return true;
                    }
                    return addPlayerToRank(sender, args[1], args[2]);
                case "assign":
                    if (!sender.hasPermission("excreleperms.add")) {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to assign ranks!");
                        return true;
                    }
                    if (args.length < 3) {
                        sender.sendMessage(ChatColor.RED + "Usage: /rank assign <player> <rank>");
                        return true;
                    }
                    return assignPlayerRank(sender, args[1], args[2]);
                case "promote":
                    if (!sender.hasPermission("excreleperms.promote")) {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to promote players!");
                        return true;
                    }
                    if (args.length < 2) {
                        sender.sendMessage(ChatColor.RED + "Usage: /rank promote <player>");
                        return true;
                    }
                    return promotePlayer(sender, args[1]);
                case "demote":
                    if (!sender.hasPermission("excreleperms.demote")) {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to demote players!");
                        return true;
                    }
                    if (args.length < 2) {
                        sender.sendMessage(ChatColor.RED + "Usage: /rank demote <player>");
                        return true;
                    }
                    return demotePlayer(sender, args[1]);
                case "reload":
                    if (!sender.hasPermission("excreleperms.reload")) {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to reload the configuration!");
                        return true;
                    }
                    reloadConfigFile(sender);
                    return true;
                case "list":
                    if (!sender.hasPermission("excreleperms.list")) {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to list ranks!");
                        return true;
                    }
                    return listRanks(sender);
                case "info":
                    if (!sender.hasPermission("excreleperms.info")) {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to view rank info!");
                        return true;
                    }
                    if (args.length < 2) {
                        sender.sendMessage(ChatColor.RED + "Usage: /rank info <rank>");
                        return true;
                    }
                    return showRankInfo(sender, args[1]);
                case "check":
                    if (!sender.hasPermission("excreleperms.check")) {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to check player ranks!");
                        return true;
                    }
                    if (args.length < 2) {
                        sender.sendMessage(ChatColor.RED + "Usage: /rank check <player>");
                        return true;
                    }
                    return checkPlayerRank(sender, args[1]);
                case "backup":
                    if (!sender.hasPermission("excreleperms.backup")) {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to manage backups!");
                        return true;
                    }
                    return handleBackupCommand(sender, args);
                case "validate":
                    if (!sender.hasPermission("excreleperms.validate")) {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to validate configuration!");
                        return true;
                    }
                    return handleValidateCommand(sender);
                case "export":
                    if (!sender.hasPermission("excreleperms.export")) {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to export data!");
                        return true;
                    }
                    return handleExportCommand(sender, args);
                case "import":
                    if (!sender.hasPermission("excreleperms.import")) {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to import data!");
                        return true;
                    }
                    return handleImportCommand(sender, args);
                case "history":
                    if (!sender.hasPermission("excreleperms.history")) {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to view history!");
                        return true;
                    }
                    return handleHistoryCommand(sender, args);
                case "format":
                    if (!sender.hasPermission("excreleperms.format")) {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to format files!");
                        return true;
                    }
                    return handleFormatCommand(sender, args);
                case "create":
                    if (!sender.hasPermission("excreleperms.create")) {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to create ranks!");
                        return true;
                    }
                    return handleCreateRankCommand(sender, args);
                case "delete":
                    if (!sender.hasPermission("excreleperms.delete")) {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to delete ranks!");
                        return true;
                    }
                    return handleDeleteRankCommand(sender, args);
                case "edit":
                    if (!sender.hasPermission("excreleperms.edit")) {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to edit ranks!");
                        return true;
                    }
                    return handleEditRankCommand(sender, args);
                case "clone":
                    if (!sender.hasPermission("excreleperms.clone")) {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to clone ranks!");
                        return true;
                    }
                    return handleCloneRankCommand(sender, args);
                case "temp":
                    if (!sender.hasPermission("excreleperms.temp")) {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to manage temporary ranks!");
                        return true;
                    }
                    return handleTemporaryRankCommand(sender, args);
                case "track":
                    if (!sender.hasPermission("excreleperms.track")) {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to manage tracks!");
                        return true;
                    }
                    return handleTrackCommand(sender, args);
                case "permission":
                    if (!sender.hasPermission("excreleperms.permission")) {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to manage permissions!");
                        return true;
                    }
                    return handlePermissionCommand(sender, args);
                case "inherit":
                    if (!sender.hasPermission("excreleperms.inherit")) {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to manage inheritance!");
                        return true;
                    }
                    return handleInheritanceCommand(sender, args);
                case "files":
                    if (!sender.hasPermission("excreleperms.files")) {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to manage files!");
                        return true;
                    }
                    return handleFilesCommand(sender, args);
                case "logs":
                    if (!sender.hasPermission("excreleperms.logs")) {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to view logs!");
                        return true;
                    }
                    return handleLogsCommand(sender, args);
                case "comment":
                    if (!sender.hasPermission("excreleperms.comment")) {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to manage comments!");
                        return true;
                    }
                    return handleCommentCommand(sender, args);
                case "setpriority":
                case "priority":
                    if (!sender.hasPermission("excreleperms.priority")) {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to set rank priority!");
                        return true;
                    }
                    return handleSetPriorityCommand(sender, args);
                case "bulk":
                    if (!sender.hasPermission("excreleperms.bulk")) {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to use bulk operations!");
                        return true;
                    }
                    return handleBulkCommand(sender, args);
                case "help":
                    return handleHelpCommand(sender, args);
                case "tag":
                    if (!sender.hasPermission("excreleperms.tag")) {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to manage tags!");
                        return true;
                    }
                    return handleTagCommand(sender, args);
                case "setcolor":
                case "color":
                    if (!sender.hasPermission("excreleperms.color")) {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to set rank colors!");
                        return true;
                    }
                    return handleColorCommand(sender, args);
                case "requirements":
                case "req":
                    if (!sender.hasPermission("excreleperms.requirements")) {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to manage requirements!");
                        return true;
                    }
                    return handleRequirementsCommand(sender, args);
                case "setexpiry":
                case "expiry":
                    if (!sender.hasPermission("excreleperms.expiry")) {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to set rank expiry!");
                        return true;
                    }
                    return handleExpiryCommand(sender, args);
                case "setprice":
                case "price":
                    if (!sender.hasPermission("excreleperms.price")) {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to set rank prices!");
                        return true;
                    }
                    return handleSetPriceCommand(sender, args);
                case "batch":
                    if (!sender.hasPermission("excreleperms.batch")) {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to use batch mode!");
                        return true;
                    }
                    return handleBatchCommand(sender, args);
                case "cache":
                    if (!sender.hasPermission("excreleperms.cache")) {
                        sender.sendMessage(com.excrele.managers.ErrorMessagesManager.getPermissionDeniedError("excreleperms.cache"));
                        return true;
                    }
                    return handleCacheCommand(sender, args);
                case "config":
                    if (!sender.hasPermission("excreleperms.config")) {
                        sender.sendMessage(com.excrele.managers.ErrorMessagesManager.getPermissionDeniedError("excreleperms.config"));
                        return true;
                    }
                    return handleConfigCommand(sender, args);
                case "gui":
                    if (!sender.hasPermission("excreleperms.gui")) {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to use the GUI!");
                        return true;
                    }
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
                        return true;
                    }
                    rankGUI.openMainGUI((Player) sender);
                    return true;
                case "buy":
                    if (!sender.hasPermission("excreleperms.buy")) {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to buy ranks!");
                        return true;
                    }
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
                        return true;
                    }
                    return handleBuyCommand(sender, args);
                default:
                    sender.sendMessage(ChatColor.RED + "Unknown subcommand! Use /rank help for a list of commands.");
                    return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String rank = ranksConfig.getString("players." + player.getUniqueId() + ".rank", "default");
        String prefix = ranksConfig.getString("ranks." + rank + ".info.prefix", "");
        String suffix = ranksConfig.getString("ranks." + rank + ".info.suffix", "");

        // Translate color codes
        prefix = ChatColor.translateAlternateColorCodes('&', prefix);
        suffix = ChatColor.translateAlternateColorCodes('&', suffix);

        // Set custom join message with prefix and suffix
        String joinMessage = prefix + player.getName() + suffix + " joined the game";
        event.setJoinMessage(joinMessage);

        // Load permissions and display name (prefix only, suffix for chat)
        loadPlayerPermissions(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String rank = ranksConfig.getString("players." + player.getUniqueId() + ".rank", "default");
        String prefix = ranksConfig.getString("ranks." + rank + ".info.prefix", "");
        String suffix = ranksConfig.getString("ranks." + rank + ".info.suffix", "");

        // Translate color codes
        prefix = ChatColor.translateAlternateColorCodes('&', prefix);
        suffix = ChatColor.translateAlternateColorCodes('&', suffix);

        // Set custom quit message with prefix and suffix
        String quitMessage = prefix + player.getName() + suffix + " left the game";
        event.setQuitMessage(quitMessage);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();
        String rank = ranksConfig.getString("players." + player.getUniqueId() + ".rank", "default");
        String prefix = ranksConfig.getString("ranks." + rank + ".info.prefix", "");
        String suffix = ranksConfig.getString("ranks." + rank + ".info.suffix", "");

        // Translate color codes
        prefix = ChatColor.translateAlternateColorCodes('&', prefix);
        suffix = ChatColor.translateAlternateColorCodes('&', suffix);

        // Use setFormat instead of cancelling - more compatible with other plugins
        String format = prefix + "%1$s" + suffix + ": %2$s";
        event.setFormat(format);
    }

    private boolean addPlayerToRank(CommandSender sender, String playerName, String rank) {
        // Validate inputs
        if (playerName == null || playerName.trim().isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Player name cannot be empty!");
            return true;
        }
        
        if (rank == null || rank.trim().isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Rank name cannot be empty!");
            return true;
        }
        
        rank = rank.trim();
        playerName = playerName.trim();
        
        // Validate rank exists
        if (!ranksConfig.contains("ranks." + rank)) {
            sender.sendMessage(ChatColor.RED + "Rank '" + rank + "' does not exist!");
            return true;
        }

        // Try to find player (online or offline)
        UUID playerUUID = null;
        Player onlinePlayer = getServer().getPlayer(playerName);
        
        if (onlinePlayer != null) {
            playerUUID = onlinePlayer.getUniqueId();
        } else {
            // Try UUID lookup
            try {
                playerUUID = UUID.fromString(playerName);
            } catch (IllegalArgumentException e) {
                // Not a UUID, try offline player lookup
                OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(playerName);
                if (offlinePlayer.hasPlayedBefore()) {
                    playerUUID = offlinePlayer.getUniqueId();
                } else {
                    sender.sendMessage(ChatColor.RED + "Player '" + playerName + "' not found! (Player must have joined the server before)");
                    return true;
                }
            }
        }

        String oldRank = ranksConfig.getString("players." + playerUUID + ".rank", "default");
        
        // Get offline player for events
        OfflinePlayer targetPlayer = onlinePlayer != null ? onlinePlayer : getServer().getOfflinePlayer(playerUUID);
        
        // Fire pre-event
        com.excrele.events.RankPreAddEvent preEvent = new com.excrele.events.RankPreAddEvent(
            targetPlayer, oldRank, rank, sender.getName(), "Manual assignment");
        getServer().getPluginManager().callEvent(preEvent);
        
        if (preEvent.isCancelled()) {
            sender.sendMessage(ChatColor.RED + "Rank change was cancelled!");
            return true;
        }
        
        ranksConfig.set("players." + playerUUID + ".rank", rank);
        
        // Create backup before major operation
        if (backupManager != null) {
            backupManager.createAutoBackup();
        }
        
        try {
            saveConfigFileSync();
        } catch (Exception e) {
            sender.sendMessage(com.excrele.managers.ErrorMessagesManager.getFileError("ranks.yml", "saving"));
            getLogger().severe("Error saving ranks.yml: " + e.getMessage());
            return true;
        }
        
        // Record history
        if (historyManager != null) {
            historyManager.recordRankChange(playerUUID, oldRank, rank, sender.getName(), "Manual assignment");
        }
        
        // Log the change
        if (loggingManager != null) {
            String playerDisplayName = onlinePlayer != null ? onlinePlayer.getName() : playerName;
            loggingManager.logRankChange(playerDisplayName, playerUUID.toString(), oldRank, rank, 
                sender.getName(), "Manual assignment", YAMLLoggingManager.LogLevel.INFO);
        }

        // If player is online, update their permissions
        if (onlinePlayer != null) {
            loadPlayerPermissions(onlinePlayer);
        }
        
        // Fire post-event
        com.excrele.events.RankPostAddEvent postEvent = new com.excrele.events.RankPostAddEvent(
            targetPlayer, oldRank, rank, sender.getName(), "Manual assignment");
        getServer().getPluginManager().callEvent(postEvent);
        
        // Also fire legacy RankAddEvent for backward compatibility
        if (onlinePlayer != null) {
            RankAddEvent event = new RankAddEvent(onlinePlayer, rank, oldRank, sender);
            getServer().getPluginManager().callEvent(event);
        } else {
            // Fire event with offline player if possible
            OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(playerUUID);
            if (offlinePlayer instanceof Player) {
                RankAddEvent event = new RankAddEvent((Player) offlinePlayer, rank, oldRank, sender);
                getServer().getPluginManager().callEvent(event);
            }
        }

        sender.sendMessage(ChatColor.GREEN + "Assigned " + playerName + " to rank " + rank);
        return true;
    }

    private boolean assignPlayerRank(CommandSender sender, String playerName, String rank) {
        return addPlayerToRank(sender, playerName, rank); // Same logic as add
    }

    private boolean promotePlayer(CommandSender sender, String playerName) {
        // Try to find player (online or offline)
        UUID playerUUID = null;
        Player onlinePlayer = getServer().getPlayer(playerName);
        
        if (onlinePlayer != null) {
            playerUUID = onlinePlayer.getUniqueId();
        } else {
            // Try UUID lookup
            try {
                playerUUID = UUID.fromString(playerName);
            } catch (IllegalArgumentException e) {
                // Not a UUID, try offline player lookup
                OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(playerName);
                if (offlinePlayer.hasPlayedBefore()) {
                    playerUUID = offlinePlayer.getUniqueId();
                } else {
                    sender.sendMessage(ChatColor.RED + "Player '" + playerName + "' not found! (Player must have joined the server before)");
            return true;
        }
            }
        }

        String currentRank = ranksConfig.getString("players." + playerUUID + ".rank", "default");
        
        // Get track for the rank (or use defaultTrack)
        String trackName = trackManager.getRankTrack(currentRank);
        if (trackName == null || trackName.isEmpty()) {
            trackName = "defaultTrack";
        }
        
        String nextRank = trackManager.getNextRank(currentRank, trackName);
        if (nextRank == null) {
            sender.sendMessage(ChatColor.RED + "Cannot promote " + playerName + " further!");
            return true;
        }
        ranksConfig.set("players." + playerUUID + ".rank", nextRank);
        
        // Create backup before major operation
        if (backupManager != null) {
            backupManager.createAutoBackup();
        }
        
        try {
            saveConfigFileSync();
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error saving configuration: " + e.getMessage());
            getLogger().severe("Error saving ranks.yml: " + e.getMessage());
            return true;
        }
        
        // Record history
        if (historyManager != null) {
            historyManager.recordRankChange(playerUUID, currentRank, nextRank, sender.getName(), "Promotion");
        }
        
        // Log the change
        if (loggingManager != null) {
            String playerDisplayName = onlinePlayer != null ? onlinePlayer.getName() : playerName;
            loggingManager.logRankChange(playerDisplayName, playerUUID.toString(), currentRank, nextRank, 
                sender.getName(), "Promotion", YAMLLoggingManager.LogLevel.INFO);
        }

        // If player is online, update their permissions
        if (onlinePlayer != null) {
            loadPlayerPermissions(onlinePlayer);
        // Fire RankPromoteEvent
            RankPromoteEvent event = new RankPromoteEvent(onlinePlayer, currentRank, nextRank, sender);
        getServer().getPluginManager().callEvent(event);
        }

        sender.sendMessage(ChatColor.GREEN + "Promoted " + playerName + " to " + nextRank);
        return true;
    }

    private boolean demotePlayer(CommandSender sender, String playerName) {
        // Try to find player (online or offline)
        UUID playerUUID = null;
        Player onlinePlayer = getServer().getPlayer(playerName);
        
        if (onlinePlayer != null) {
            playerUUID = onlinePlayer.getUniqueId();
        } else {
            // Try UUID lookup
            try {
                playerUUID = UUID.fromString(playerName);
            } catch (IllegalArgumentException e) {
                // Not a UUID, try offline player lookup
                OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(playerName);
                if (offlinePlayer.hasPlayedBefore()) {
                    playerUUID = offlinePlayer.getUniqueId();
                } else {
                    sender.sendMessage(ChatColor.RED + "Player '" + playerName + "' not found! (Player must have joined the server before)");
            return true;
        }
            }
        }

        String currentRank = ranksConfig.getString("players." + playerUUID + ".rank", "default");
        
        // Get track for the rank (or use defaultTrack)
        String trackName = trackManager.getRankTrack(currentRank);
        if (trackName == null || trackName.isEmpty()) {
            trackName = "defaultTrack";
        }
        
        String previousRank = trackManager.getPreviousRank(currentRank, trackName);
        if (previousRank == null) {
            sender.sendMessage(ChatColor.RED + "Cannot demote " + playerName + " further!");
            return true;
        }
        ranksConfig.set("players." + playerUUID + ".rank", previousRank);
        
        // Create backup before major operation
        if (backupManager != null) {
            backupManager.createAutoBackup();
        }
        
        try {
            saveConfigFileSync();
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error saving configuration: " + e.getMessage());
            getLogger().severe("Error saving ranks.yml: " + e.getMessage());
            return true;
        }
        
        // Record history
        if (historyManager != null) {
            historyManager.recordRankChange(playerUUID, currentRank, previousRank, sender.getName(), "Demotion");
        }
        
        // Log the change
        if (loggingManager != null) {
            String playerDisplayName = onlinePlayer != null ? onlinePlayer.getName() : playerName;
            loggingManager.logRankChange(playerDisplayName, playerUUID.toString(), currentRank, previousRank, 
                sender.getName(), "Demotion", YAMLLoggingManager.LogLevel.INFO);
        }

        // If player is online, update their permissions
        if (onlinePlayer != null) {
            loadPlayerPermissions(onlinePlayer);
        // Fire RankDemoteEvent
            RankDemoteEvent event = new RankDemoteEvent(onlinePlayer, currentRank, previousRank, sender);
        getServer().getPluginManager().callEvent(event);
        }

        sender.sendMessage(ChatColor.GREEN + "Demoted " + playerName + " to " + previousRank);
        return true;
    }

    private void reloadConfigFile(CommandSender sender) {
        if (yamlFileManager != null) {
            ranksConfig = yamlFileManager.reloadConfig("ranks.yml");
        } else {
        ranksConfig = YamlConfiguration.loadConfiguration(configFile);
        }
        getServer().getOnlinePlayers().forEach(this::loadPlayerPermissions);
        // Fire RankReloadEvent
        RankReloadEvent event = new RankReloadEvent(sender);
        getServer().getPluginManager().callEvent(event);
        sender.sendMessage(ChatColor.GREEN + "Ranks configuration reloaded!");
    }

    public void loadPlayerPermissions(Player player) {
        // Remove existing permissions
        if (playerPermissions.containsKey(player.getUniqueId())) {
            playerPermissions.get(player.getUniqueId()).remove();
            playerPermissions.remove(player.getUniqueId());
        }
        
        // Invalidate permission cache
        if (permissionCacheManager != null) {
            permissionCacheManager.invalidateCache(player.getUniqueId());
        }

        // Load new permissions
        String rank = ranksConfig.getString("players." + player.getUniqueId() + ".rank", "default");
        PermissionAttachment attachment = player.addAttachment(this);
        playerPermissions.put(player.getUniqueId(), attachment);

        // Apply permissions
        List<String> permissions = ranksConfig.getStringList("ranks." + rank + ".permissions");
        for (String perm : permissions) {
            if (perm.startsWith("-")) {
                attachment.unsetPermission(perm.substring(1));
            } else {
                attachment.setPermission(perm, true);
            }
        }

        // Apply inherited permissions
        List<String> inheritances = ranksConfig.getStringList("ranks." + rank + ".inheritance");
        for (String inheritedRank : inheritances) {
            List<String> inheritedPerms = ranksConfig.getStringList("ranks." + inheritedRank + ".permissions");
            for (String perm : inheritedPerms) {
                if (perm.startsWith("-")) {
                    attachment.unsetPermission(perm.substring(1));
                } else {
                    attachment.setPermission(perm, true);
                }
            }
        }

        // Set prefix for display name (tab list and above head) - no suffix here
        String prefix = ranksConfig.getString("ranks." + rank + ".info.prefix", "");
        player.setDisplayName(ChatColor.translateAlternateColorCodes('&', prefix + player.getName()));
        
        // Update tab list name with priority-based sorting
        updateTabListName(player, rank);
    }
    
    /**
     * Update player's tab list name based on rank priority.
     */
    private void updateTabListName(Player player, String rank) {
        String prefix = ranksConfig.getString("ranks." + rank + ".info.prefix", "");
        String displayName = ChatColor.translateAlternateColorCodes('&', prefix + player.getName());
        
        // Set tab list name (this affects tab list order in some implementations)
        try {
            player.setPlayerListName(displayName);
        } catch (Exception e) {
            // Fallback if setPlayerListName is not available
        }
    }
    
    /**
     * Sort players in tab list by rank priority.
     * This is called periodically to maintain proper order.
     */
    private void sortTabListByPriority() {
        getServer().getScheduler().runTask(this, () -> {
            List<Player> players = new ArrayList<>(getServer().getOnlinePlayers());
            
            // Sort by rank priority (higher priority first)
            players.sort((p1, p2) -> {
                String rank1 = ranksConfig.getString("players." + p1.getUniqueId() + ".rank", "default");
                String rank2 = ranksConfig.getString("players." + p2.getUniqueId() + ".rank", "default");
                
                int priority1 = ranksConfig.getInt("ranks." + rank1 + ".priority", 0);
                int priority2 = ranksConfig.getInt("ranks." + rank2 + ".priority", 0);
                
                // Higher priority first, then by name
                if (priority1 != priority2) {
                    return Integer.compare(priority2, priority1);
                }
                return p1.getName().compareToIgnoreCase(p2.getName());
            });
            
            // Update tab list names in order
            for (int i = 0; i < players.size(); i++) {
                Player player = players.get(i);
                String rank = ranksConfig.getString("players." + player.getUniqueId() + ".rank", "default");
                updateTabListName(player, rank);
            }
        });
    }

    private void saveConfigFile() throws IOException {
        // Use YAML manager for async save if enabled
        if (yamlFileManager != null && yamlFileManager.getConfig("config.yml").getBoolean("async-operations", true)) {
            yamlFileManager.saveConfig("ranks.yml", ranksConfig);
        } else {
            ranksConfig.save(configFile);
        }
    }
    
    private boolean saveConfigFileSync() {
        try {
            if (yamlFileManager != null) {
                return yamlFileManager.saveConfigSync("ranks.yml", ranksConfig);
            } else {
                ranksConfig.save(configFile);
                return true;
            }
        } catch (IOException e) {
            getLogger().severe("Failed to save config: " + e.getMessage());
            return false;
        }
    }

    private boolean listRanks(CommandSender sender) {
        if (!ranksConfig.contains("ranks")) {
            sender.sendMessage(ChatColor.RED + "No ranks configured!");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "=== Available Ranks ===");
        for (String rank : ranksConfig.getConfigurationSection("ranks").getKeys(false)) {
            String prefix = ranksConfig.getString("ranks." + rank + ".info.prefix", "");
            if (!prefix.isEmpty()) {
                prefix = ChatColor.translateAlternateColorCodes('&', prefix);
            }
            sender.sendMessage(ChatColor.YELLOW + "- " + rank + (prefix.isEmpty() ? "" : " " + prefix + rank + ChatColor.RESET));
        }

        // Show progression track if exists
        List<String> track = ranksConfig.getStringList("progression-tracks.defaultTrack");
        if (!track.isEmpty()) {
            sender.sendMessage(ChatColor.GOLD + "\n=== Progression Track ===");
            StringBuilder trackStr = new StringBuilder();
            for (int i = 0; i < track.size(); i++) {
                if (i > 0) trackStr.append(" → ");
                trackStr.append(track.get(i));
            }
            sender.sendMessage(ChatColor.YELLOW + trackStr.toString());
        }

        return true;
    }

    private boolean showRankInfo(CommandSender sender, String rankName) {
        // Validate input
        if (rankName == null || rankName.trim().isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Rank name cannot be empty!");
            return true;
        }
        
        rankName = rankName.trim();
        
        if (!ranksConfig.contains("ranks." + rankName)) {
            sender.sendMessage(ChatColor.RED + "Rank '" + rankName + "' does not exist!");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "=== Rank Info: " + rankName + " ===");
        
        String prefix = ranksConfig.getString("ranks." + rankName + ".info.prefix", "");
        String suffix = ranksConfig.getString("ranks." + rankName + ".info.suffix", "");
        
        if (!prefix.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Prefix: " + ChatColor.translateAlternateColorCodes('&', prefix) + rankName + ChatColor.RESET);
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Prefix: " + ChatColor.GRAY + "None");
        }
        
        if (!suffix.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Suffix: " + ChatColor.translateAlternateColorCodes('&', suffix));
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Suffix: " + ChatColor.GRAY + "None");
        }

        List<String> permissions = ranksConfig.getStringList("ranks." + rankName + ".permissions");
        if (!permissions.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Permissions (" + permissions.size() + "):");
            for (String perm : permissions) {
                if (perm.startsWith("-")) {
                    sender.sendMessage(ChatColor.RED + "  - " + perm);
                } else {
                    sender.sendMessage(ChatColor.GREEN + "  + " + perm);
                }
            }
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Permissions: " + ChatColor.GRAY + "None");
        }

        List<String> inheritance = ranksConfig.getStringList("ranks." + rankName + ".inheritance");
        if (!inheritance.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Inherits from: " + ChatColor.WHITE + String.join(", ", inheritance));
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Inherits from: " + ChatColor.GRAY + "None");
        }
        
        // Display priority
        int priority = ranksConfig.getInt("ranks." + rankName + ".priority", 0);
        sender.sendMessage(ChatColor.YELLOW + "Priority: " + ChatColor.WHITE + priority + 
            ChatColor.GRAY + " (higher = more important, affects tab list order)");
        
        // Display track if set
        String track = ranksConfig.getString("ranks." + rankName + ".track", "");
        if (!track.isEmpty() && !track.equals("defaultTrack")) {
            sender.sendMessage(ChatColor.YELLOW + "Track: " + ChatColor.WHITE + track);
        }
        
        // Display comment if exists
        if (commentManager != null) {
            String comment = commentManager.getComment(rankName);
            if (comment != null && !comment.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "Comment: " + ChatColor.GRAY + comment);
            }
        }

        return true;
    }

    private boolean checkPlayerRank(CommandSender sender, String playerName) {
        // Validate input
        if (playerName == null || playerName.trim().isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Player name cannot be empty!");
            return true;
        }
        
        playerName = playerName.trim();
        
        UUID playerUUID = null;
        Player onlinePlayer = getServer().getPlayer(playerName);
        String displayName = playerName;
        
        if (onlinePlayer != null) {
            playerUUID = onlinePlayer.getUniqueId();
            displayName = onlinePlayer.getName();
        } else {
            // Try UUID lookup
            try {
                playerUUID = UUID.fromString(playerName);
                OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(playerUUID);
                if (offlinePlayer.hasPlayedBefore()) {
                    displayName = offlinePlayer.getName() != null ? offlinePlayer.getName() : playerUUID.toString();
                }
            } catch (IllegalArgumentException e) {
                // Not a UUID, try offline player lookup
                OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(playerName);
                if (offlinePlayer.hasPlayedBefore()) {
                    playerUUID = offlinePlayer.getUniqueId();
                    displayName = offlinePlayer.getName() != null ? offlinePlayer.getName() : playerName;
                } else {
                    sender.sendMessage(ChatColor.RED + "Player '" + playerName + "' not found! (Player must have joined the server before)");
                    return true;
                }
            }
        }

        String rank = ranksConfig.getString("players." + playerUUID + ".rank", "default");
        sender.sendMessage(ChatColor.GOLD + "=== Player Rank Info ===");
        sender.sendMessage(ChatColor.YELLOW + "Player: " + ChatColor.WHITE + displayName);
        sender.sendMessage(ChatColor.YELLOW + "UUID: " + ChatColor.WHITE + playerUUID.toString());
        sender.sendMessage(ChatColor.YELLOW + "Rank: " + ChatColor.WHITE + rank);
        
        if (ranksConfig.contains("ranks." + rank)) {
            String prefix = ranksConfig.getString("ranks." + rank + ".info.prefix", "");
            String suffix = ranksConfig.getString("ranks." + rank + ".info.suffix", "");
            if (!prefix.isEmpty() || !suffix.isEmpty()) {
                String formatted = ChatColor.translateAlternateColorCodes('&', prefix) + displayName + 
                                 ChatColor.translateAlternateColorCodes('&', suffix);
                sender.sendMessage(ChatColor.YELLOW + "Display: " + formatted);
            }
        }

        return true;
    }

    // ========== API Methods ==========

    /**
     * Get a player's rank by UUID (API method).
     *
     * @param uuid The player's UUID
     * @return The player's rank name, or "default" if not set
     */
    public String getPlayerRank(UUID uuid) {
        return ranksConfig.getString("players." + uuid + ".rank", "default");
    }

    /**
     * Set a player's rank by UUID (API method).
     *
     * @param uuid The player's UUID
     * @param rank The rank name
     * @return true if successful, false otherwise
     */
    public boolean setPlayerRank(UUID uuid, String rank) {
        // Validate inputs
        if (uuid == null) {
            getLogger().warning("Attempted to set rank with null UUID");
            return false;
        }
        
        if (rank == null || rank.trim().isEmpty()) {
            getLogger().warning("Attempted to set rank with null or empty rank name");
            return false;
        }
        
        rank = rank.trim();
        
        if (!rankExists(rank)) {
            getLogger().warning("Attempted to set non-existent rank: " + rank);
            return false;
        }
        
        try {
            String oldRank = ranksConfig.getString("players." + uuid + ".rank", "default");
            OfflinePlayer targetPlayer = getServer().getOfflinePlayer(uuid);
            
            // Fire pre-event
            com.excrele.events.RankPreAddEvent preEvent = new com.excrele.events.RankPreAddEvent(
                targetPlayer, oldRank, rank, "API", "API call");
            getServer().getPluginManager().callEvent(preEvent);
            
            if (preEvent.isCancelled()) {
                getLogger().warning("Rank change cancelled by event handler for UUID: " + uuid);
                return false;
            }
            
            ranksConfig.set("players." + uuid + ".rank", rank);
            saveConfigFile();
            
            // Update online player if they're online
            Player player = getServer().getPlayer(uuid);
            if (player != null) {
                loadPlayerPermissions(player);
            }
            
            // Fire post-event
            com.excrele.events.RankPostAddEvent postEvent = new com.excrele.events.RankPostAddEvent(
                targetPlayer, oldRank, rank, "API", "API call");
            getServer().getPluginManager().callEvent(postEvent);
            
            return true;
        } catch (IOException e) {
            getLogger().severe("Error saving ranks.yml: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if a rank exists (API method).
     *
     * @param rank The rank name
     * @return true if the rank exists, false otherwise
     */
    public boolean rankExists(String rank) {
        if (rank == null || rank.trim().isEmpty()) {
            return false;
        }
        return ranksConfig.contains("ranks." + rank.trim());
    }

    /**
     * Get the prefix for a rank (API method).
     *
     * @param rank The rank name
     * @return The rank prefix, or empty string if not set
     */
    public String getRankPrefix(String rank) {
        return ranksConfig.getString("ranks." + rank + ".info.prefix", "");
    }

    /**
     * Get the suffix for a rank (API method).
     *
     * @param rank The rank name
     * @return The rank suffix, or empty string if not set
     */
    public String getRankSuffix(String rank) {
        return ranksConfig.getString("ranks." + rank + ".info.suffix", "");
    }

    /**
     * Get all permissions for a rank (API method).
     *
     * @param rank The rank name
     * @return List of permissions, or empty list if rank doesn't exist
     */
    public List<String> getRankPermissions(String rank) {
        if (!rankExists(rank)) {
            return new ArrayList<>();
        }
        return ranksConfig.getStringList("ranks." + rank + ".permissions");
    }

    /**
     * Get all ranks that a rank inherits from (API method).
     *
     * @param rank The rank name
     * @return List of inherited rank names, or empty list if none
     */
    public List<String> getRankInheritance(String rank) {
        if (!rankExists(rank)) {
            return new ArrayList<>();
        }
        return ranksConfig.getStringList("ranks." + rank + ".inheritance");
    }

    /**
     * Get all available ranks (API method).
     *
     * @return List of all rank names, or empty list if none
     */
    public List<String> getAllRanks() {
        if (!ranksConfig.contains("ranks")) {
            return new ArrayList<>();
        }
        return new ArrayList<>(ranksConfig.getConfigurationSection("ranks").getKeys(false));
    }

    /**
     * Reload the ranks configuration (API method).
     *
     * @return true if successful, false otherwise
     */
    public boolean reloadRanksConfig() {
        try {
            ranksConfig = YamlConfiguration.loadConfiguration(configFile);
            getServer().getOnlinePlayers().forEach(this::loadPlayerPermissions);
            return true;
        } catch (Exception e) {
            getLogger().severe("Error reloading ranks.yml: " + e.getMessage());
            return false;
        }
    }

    // ========== Tab Completion ==========

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("rank")) {
            return null;
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String input = args[0].toLowerCase();
            
            if (sender.hasPermission("excreleperms.add") && "add".startsWith(input)) {
                completions.add("add");
            }
            if (sender.hasPermission("excreleperms.add") && "assign".startsWith(input)) {
                completions.add("assign");
            }
            if (sender.hasPermission("excreleperms.promote") && "promote".startsWith(input)) {
                completions.add("promote");
            }
            if (sender.hasPermission("excreleperms.demote") && "demote".startsWith(input)) {
                completions.add("demote");
            }
            if (sender.hasPermission("excreleperms.reload") && "reload".startsWith(input)) {
                completions.add("reload");
            }
            if (sender.hasPermission("excreleperms.list") && "list".startsWith(input)) {
                completions.add("list");
            }
            if (sender.hasPermission("excreleperms.info") && "info".startsWith(input)) {
                completions.add("info");
            }
            if (sender.hasPermission("excreleperms.check") && "check".startsWith(input)) {
                completions.add("check");
            }
            if (sender.hasPermission("excreleperms.backup") && "backup".startsWith(input)) {
                completions.add("backup");
            }
            if (sender.hasPermission("excreleperms.validate") && "validate".startsWith(input)) {
                completions.add("validate");
            }
            if (sender.hasPermission("excreleperms.export") && "export".startsWith(input)) {
                completions.add("export");
            }
            if (sender.hasPermission("excreleperms.import") && "import".startsWith(input)) {
                completions.add("import");
            }
            if (sender.hasPermission("excreleperms.history") && "history".startsWith(input)) {
                completions.add("history");
            }
            if (sender.hasPermission("excreleperms.format") && "format".startsWith(input)) {
                completions.add("format");
            }
            if (sender.hasPermission("excreleperms.create") && "create".startsWith(input)) {
                completions.add("create");
            }
            if (sender.hasPermission("excreleperms.delete") && "delete".startsWith(input)) {
                completions.add("delete");
            }
            if (sender.hasPermission("excreleperms.edit") && "edit".startsWith(input)) {
                completions.add("edit");
            }
            if (sender.hasPermission("excreleperms.clone") && "clone".startsWith(input)) {
                completions.add("clone");
            }
            if (sender.hasPermission("excreleperms.temp") && "temp".startsWith(input)) {
                completions.add("temp");
            }
            if (sender.hasPermission("excreleperms.track") && "track".startsWith(input)) {
                completions.add("track");
            }
            if (sender.hasPermission("excreleperms.permission") && "permission".startsWith(input)) {
                completions.add("permission");
            }
            if (sender.hasPermission("excreleperms.inherit") && "inherit".startsWith(input)) {
                completions.add("inherit");
            }
            if (sender.hasPermission("excreleperms.files") && "files".startsWith(input)) {
                completions.add("files");
            }
            if (sender.hasPermission("excreleperms.logs") && "logs".startsWith(input)) {
                completions.add("logs");
            }
            if (sender.hasPermission("excreleperms.comment") && "comment".startsWith(input)) {
                completions.add("comment");
            }
            if (sender.hasPermission("excreleperms.priority") && "setpriority".startsWith(input)) {
                completions.add("setpriority");
            }
            if (sender.hasPermission("excreleperms.priority") && "priority".startsWith(input)) {
                completions.add("priority");
            }
            if (sender.hasPermission("excreleperms.gui") && "gui".startsWith(input)) {
                completions.add("gui");
            }
            if (sender.hasPermission("excreleperms.buy") && "buy".startsWith(input)) {
                completions.add("buy");
            }
            if (sender.hasPermission("excreleperms.price") && "setprice".startsWith(input)) {
                completions.add("setprice");
            }
            if (sender.hasPermission("excreleperms.price") && "price".startsWith(input)) {
                completions.add("price");
            }
            
            return completions;
        } else if (args.length == 2) {
            String subcommand = args[0].toLowerCase();
            
            if (subcommand.equals("add") || subcommand.equals("assign")) {
                // Complete player names
                if (sender.hasPermission("excreleperms.add")) {
                    List<String> players = new ArrayList<>();
                    String input = args[1].toLowerCase();
                    for (Player player : getServer().getOnlinePlayers()) {
                        if (player.getName().toLowerCase().startsWith(input)) {
                            players.add(player.getName());
                        }
                    }
                    return players;
                }
            } else if (subcommand.equals("promote") || subcommand.equals("demote")) {
                // Complete player names
                if (sender.hasPermission("excreleperms.promote") || sender.hasPermission("excreleperms.demote")) {
                    List<String> players = new ArrayList<>();
                    String input = args[1].toLowerCase();
                    for (Player player : getServer().getOnlinePlayers()) {
                        if (player.getName().toLowerCase().startsWith(input)) {
                            players.add(player.getName());
                        }
                    }
                    return players;
                }
            } else if (subcommand.equals("info")) {
                // Complete rank names
                if (sender.hasPermission("excreleperms.info")) {
                    List<String> ranks = new ArrayList<>();
                    String input = args[1].toLowerCase();
                    for (String rank : getAllRanks()) {
                        if (rank.toLowerCase().startsWith(input)) {
                            ranks.add(rank);
                        }
                    }
                    return ranks;
                }
            } else if (subcommand.equals("check")) {
                // Complete player names
                if (sender.hasPermission("excreleperms.check")) {
                    List<String> players = new ArrayList<>();
                    String input = args[1].toLowerCase();
                    for (Player player : getServer().getOnlinePlayers()) {
                        if (player.getName().toLowerCase().startsWith(input)) {
                            players.add(player.getName());
                        }
                    }
                    return players;
                }
            }
        } else if (args.length == 3) {
            String subcommand = args[0].toLowerCase();
            
            if (subcommand.equals("add") || subcommand.equals("assign")) {
                // Complete rank names
                if (sender.hasPermission("excreleperms.add")) {
                    List<String> ranks = new ArrayList<>();
                    String input = args[2].toLowerCase();
                    for (String rank : getAllRanks()) {
                        if (rank.toLowerCase().startsWith(input)) {
                            ranks.add(rank);
                        }
                    }
                    return ranks;
                }
            } else if (subcommand.equals("backup")) {
                if (sender.hasPermission("excreleperms.backup")) {
                    List<String> options = new ArrayList<>();
                    String input = args[2].toLowerCase();
                    if ("restore".startsWith(input)) options.add("restore");
                    if ("delete".startsWith(input)) options.add("delete");
                    return options;
                }
            } else if (subcommand.equals("export")) {
                if (sender.hasPermission("excreleperms.export")) {
                    List<String> options = new ArrayList<>();
                    String input = args[2].toLowerCase();
                    if ("ranks".startsWith(input)) options.add("ranks");
                    if ("players".startsWith(input)) options.add("players");
                    return options;
                }
            } else if (subcommand.equals("temp")) {
                if (sender.hasPermission("excreleperms.temp")) {
                    List<String> options = new ArrayList<>();
                    String input = args[2].toLowerCase();
                    if ("assign".startsWith(input)) options.add("assign");
                    if ("add".startsWith(input)) options.add("add");
                    if ("list".startsWith(input)) options.add("list");
                    if ("cancel".startsWith(input)) options.add("cancel");
                    return options;
                }
            } else if (subcommand.equals("track")) {
                if (sender.hasPermission("excreleperms.track")) {
                    List<String> options = new ArrayList<>();
                    String input = args[2].toLowerCase();
                    if ("create".startsWith(input)) options.add("create");
                    if ("list".startsWith(input)) options.add("list");
                    if ("delete".startsWith(input)) options.add("delete");
                    return options;
                }
            } else if (subcommand.equals("permission")) {
                if (sender.hasPermission("excreleperms.permission")) {
                    List<String> options = new ArrayList<>();
                    String input = args[2].toLowerCase();
                    if ("add".startsWith(input)) options.add("add");
                    if ("remove".startsWith(input)) options.add("remove");
                    if ("list".startsWith(input)) options.add("list");
                    if ("clear".startsWith(input)) options.add("clear");
                    return options;
                }
            } else if (subcommand.equals("inherit")) {
                if (sender.hasPermission("excreleperms.inherit")) {
                    List<String> options = new ArrayList<>();
                    String input = args[2].toLowerCase();
                    if ("add".startsWith(input)) options.add("add");
                    if ("remove".startsWith(input)) options.add("remove");
                    if ("list".startsWith(input)) options.add("list");
                    return options;
                }
            } else if (subcommand.equals("files")) {
                if (sender.hasPermission("excreleperms.files")) {
                    List<String> options = new ArrayList<>();
                    String input = args[2].toLowerCase();
                    if ("split".startsWith(input)) options.add("split");
                    if ("merge".startsWith(input)) options.add("merge");
                    return options;
                }
            } else if (subcommand.equals("logs")) {
                if (sender.hasPermission("excreleperms.logs")) {
                    List<String> options = new ArrayList<>();
                    String input = args[2].toLowerCase();
                    if ("view".startsWith(input)) options.add("view");
                    if ("export".startsWith(input)) options.add("export");
                    if ("list".startsWith(input)) options.add("list");
                    return options;
                }
            } else if (subcommand.equals("comment")) {
                if (sender.hasPermission("excreleperms.comment")) {
                    List<String> options = new ArrayList<>();
                    String input = args[2].toLowerCase();
                    if ("add".startsWith(input)) options.add("add");
                    if ("remove".startsWith(input)) options.add("remove");
                    if ("view".startsWith(input)) options.add("view");
                    return options;
                }
            } else if (subcommand.equals("setpriority") || subcommand.equals("priority")) {
                // Complete rank names
                if (sender.hasPermission("excreleperms.priority")) {
                    List<String> ranks = new ArrayList<>();
                    String input = args[2].toLowerCase();
                    for (String rank : getAllRanks()) {
                        if (rank.toLowerCase().startsWith(input)) {
                            ranks.add(rank);
                        }
                    }
                    return ranks;
                }
            } else if (subcommand.equals("delete")) {
                // Complete rank names
                if (sender.hasPermission("excreleperms.delete")) {
                    List<String> ranks = new ArrayList<>();
                    String input = args[2].toLowerCase();
                    for (String rank : getAllRanks()) {
                        if (rank.toLowerCase().startsWith(input)) {
                            ranks.add(rank);
                        }
                    }
                    return ranks;
                }
            } else if (subcommand.equals("edit") || subcommand.equals("clone")) {
                // Complete rank names
                if (sender.hasPermission("excreleperms.edit") || sender.hasPermission("excreleperms.clone")) {
                    List<String> ranks = new ArrayList<>();
                    String input = args[2].toLowerCase();
                    for (String rank : getAllRanks()) {
                        if (rank.toLowerCase().startsWith(input)) {
                            ranks.add(rank);
                        }
                    }
                    return ranks;
                }
            } else if (subcommand.equals("permission") || subcommand.equals("inherit")) {
                // Complete rank names for permission/inherit commands
                if (sender.hasPermission("excreleperms.permission") || sender.hasPermission("excreleperms.inherit")) {
                    List<String> ranks = new ArrayList<>();
                    String input = args[2].toLowerCase();
                    for (String rank : getAllRanks()) {
                        if (rank.toLowerCase().startsWith(input)) {
                            ranks.add(rank);
                        }
                    }
                    return ranks;
                }
            }
        } else if (args.length == 4) {
            String subcommand = args[0].toLowerCase();
            
            if (subcommand.equals("edit")) {
                if (sender.hasPermission("excreleperms.edit")) {
                    List<String> properties = new ArrayList<>();
                    String input = args[3].toLowerCase();
                    if ("prefix".startsWith(input)) properties.add("prefix");
                    if ("suffix".startsWith(input)) properties.add("suffix");
                    if ("priority".startsWith(input)) properties.add("priority");
                    if ("track".startsWith(input)) properties.add("track");
                    return properties;
                }
            } else if (subcommand.equals("temp") && (args[2].equalsIgnoreCase("assign") || args[2].equalsIgnoreCase("add"))) {
                // Complete player names for temp assign
                if (sender.hasPermission("excreleperms.temp")) {
                    List<String> players = new ArrayList<>();
                    String input = args[3].toLowerCase();
                    for (Player player : getServer().getOnlinePlayers()) {
                        if (player.getName().toLowerCase().startsWith(input)) {
                            players.add(player.getName());
                        }
                    }
                    return players;
                }
            } else if (subcommand.equals("comment")) {
                if (args.length == 3) {
                    // Complete rank names for comment command
                    if (sender.hasPermission("excreleperms.comment")) {
                        List<String> ranks = new ArrayList<>();
                        String input = args[2].toLowerCase();
                        for (String rank : getAllRanks()) {
                            if (rank.toLowerCase().startsWith(input)) {
                                ranks.add(rank);
                            }
                        }
                        return ranks;
                    }
                }
            } else if (subcommand.equals("permission") && args[2].equalsIgnoreCase("add")) {
                // Complete rank names
                if (sender.hasPermission("excreleperms.permission")) {
                    List<String> ranks = new ArrayList<>();
                    String input = args[3].toLowerCase();
                    for (String rank : getAllRanks()) {
                        if (rank.toLowerCase().startsWith(input)) {
                            ranks.add(rank);
                        }
                    }
                    return ranks;
                }
            } else if (subcommand.equals("inherit") && args[2].equalsIgnoreCase("add")) {
                // Complete rank names
                if (sender.hasPermission("excreleperms.inherit")) {
                    List<String> ranks = new ArrayList<>();
                    String input = args[3].toLowerCase();
                    for (String rank : getAllRanks()) {
                        if (rank.toLowerCase().startsWith(input)) {
                            ranks.add(rank);
                        }
                    }
                    return ranks;
                }
            }
        }
        
        return new ArrayList<>();
    }
    
    // ========== New YAML Command Handlers ==========
    
    private boolean handleBackupCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank backup <create|list|restore|delete> [name]");
            return true;
        }
        
        switch (args[1].toLowerCase()) {
            case "create":
                sender.sendMessage(ChatColor.YELLOW + "Creating backup...");
                backupManager.createBackup().thenAccept(backupName -> {
                    if (backupName != null) {
                        getServer().getScheduler().runTask(this, () -> {
                            sender.sendMessage(ChatColor.GREEN + "Backup created: " + backupName);
                        });
                    } else {
                        getServer().getScheduler().runTask(this, () -> {
                            sender.sendMessage(ChatColor.RED + "Failed to create backup!");
                        });
                    }
                });
                return true;
            case "list":
                List<String> backups = backupManager.listBackups();
                if (backups.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "No backups found.");
                } else {
                    sender.sendMessage(ChatColor.GOLD + "=== Backups ===");
                    for (String backup : backups) {
                        sender.sendMessage(ChatColor.YELLOW + "- " + backup);
                    }
                }
                return true;
            case "restore":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rank backup restore <backup-name>");
                    return true;
                }
                sender.sendMessage(ChatColor.YELLOW + "Restoring backup...");
                backupManager.restoreBackup(args[2]).thenAccept(success -> {
                    getServer().getScheduler().runTask(this, () -> {
                        if (success) {
                            sender.sendMessage(ChatColor.GREEN + "Backup restored! Please reload the plugin.");
                        } else {
                            sender.sendMessage(ChatColor.RED + "Failed to restore backup!");
                        }
                    });
                });
                return true;
            case "delete":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rank backup delete <backup-name>");
                    return true;
                }
                if (backupManager.deleteBackup(args[2])) {
                    sender.sendMessage(ChatColor.GREEN + "Backup deleted: " + args[2]);
                } else {
                    sender.sendMessage(ChatColor.RED + "Failed to delete backup!");
                }
                return true;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown backup subcommand!");
                return true;
        }
    }
    
    private boolean handleValidateCommand(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Validating configuration...");
        
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            YAMLValidationManager.ValidationResult result = validationManager.validateAll();
            
            getServer().getScheduler().runTask(this, () -> {
                if (result.isValid()) {
                    sender.sendMessage(ChatColor.GREEN + "✓ Configuration is valid!");
                    if (!result.getWarnings().isEmpty()) {
                        sender.sendMessage(ChatColor.YELLOW + "Warnings:");
                        for (String warning : result.getWarnings()) {
                            sender.sendMessage(ChatColor.YELLOW + "  - " + warning);
                        }
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "✗ Configuration has errors:");
                    for (String error : result.getErrors()) {
                        sender.sendMessage(ChatColor.RED + "  - " + error);
                    }
                    if (!result.getWarnings().isEmpty()) {
                        sender.sendMessage(ChatColor.YELLOW + "Warnings:");
                        for (String warning : result.getWarnings()) {
                            sender.sendMessage(ChatColor.YELLOW + "  - " + warning);
                        }
                    }
                }
            });
        });
        
        return true;
    }
    
    private boolean handleExportCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank export <ranks|players> [filename]");
            return true;
        }
        
        String type = args[1].toLowerCase();
        String filename = args.length > 2 ? args[2] : "export_" + type + "_" + System.currentTimeMillis() + ".yml";
        File exportFile = new File(getDataFolder(), "exports/" + filename);
        
        sender.sendMessage(ChatColor.YELLOW + "Exporting " + type + "...");
        
        CompletableFuture<Boolean> future;
        if (type.equals("ranks")) {
            future = exportImportManager.exportRanks(exportFile);
        } else if (type.equals("players")) {
            future = exportImportManager.exportPlayers(exportFile);
        } else {
            sender.sendMessage(ChatColor.RED + "Unknown export type! Use 'ranks' or 'players'");
            return true;
        }
        
        future.thenAccept(success -> {
            getServer().getScheduler().runTask(this, () -> {
                if (success) {
                    sender.sendMessage(ChatColor.GREEN + "Export completed: " + filename);
                } else {
                    sender.sendMessage(ChatColor.RED + "Export failed!");
                }
            });
        });
        
        return true;
    }
    
    private boolean handleImportCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank import <filename> [merge]");
            return true;
        }
        
        String filename = args[1];
        boolean merge = args.length > 2 && args[2].equalsIgnoreCase("merge");
        File importFile = new File(getDataFolder(), "exports/" + filename);
        
        if (!importFile.exists()) {
            // Try in data folder directly
            importFile = new File(getDataFolder(), filename);
        }
        
        if (!importFile.exists()) {
            sender.sendMessage(ChatColor.RED + "Import file not found: " + filename);
            return true;
        }
        
        sender.sendMessage(ChatColor.YELLOW + "Importing from " + filename + "...");
        
        exportImportManager.importFromFile(importFile, merge).thenAccept(success -> {
            getServer().getScheduler().runTask(this, () -> {
                if (success) {
                    sender.sendMessage(ChatColor.GREEN + "Import completed! Reloading configuration...");
                    reloadConfigFile(sender);
                } else {
                    sender.sendMessage(ChatColor.RED + "Import failed!");
                }
            });
        });
        
        return true;
    }
    
    private boolean handleHistoryCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank history <player> [clear]");
            return true;
        }
        
        String playerName = args[1];
        UUID playerUUID = null;
        
        Player onlinePlayer = getServer().getPlayer(playerName);
        if (onlinePlayer != null) {
            playerUUID = onlinePlayer.getUniqueId();
        } else {
            try {
                playerUUID = UUID.fromString(playerName);
            } catch (IllegalArgumentException e) {
                OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(playerName);
                if (offlinePlayer.hasPlayedBefore()) {
                    playerUUID = offlinePlayer.getUniqueId();
                } else {
                    sender.sendMessage(ChatColor.RED + "Player not found!");
                    return true;
                }
            }
        }
        
        if (args.length > 2 && args[2].equalsIgnoreCase("clear")) {
            historyManager.clearPlayerHistory(playerUUID);
            sender.sendMessage(ChatColor.GREEN + "History cleared for " + playerName);
            return true;
        }
        
        List<YAMLHistoryManager.HistoryEntry> history = historyManager.getPlayerHistory(playerUUID);
        
        if (history.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No history found for " + playerName);
            return true;
        }
        
        sender.sendMessage(ChatColor.GOLD + "=== Rank History: " + playerName + " ===");
        for (YAMLHistoryManager.HistoryEntry entry : history) {
            sender.sendMessage(ChatColor.YELLOW + entry.getTimestamp() + ": " + 
                entry.getOldRank() + " → " + entry.getNewRank() + 
                " (by " + entry.getSender() + ")");
        }
        
        return true;
    }
    
    private boolean handleFormatCommand(CommandSender sender, String[] args) {
        String fileName = args.length > 1 ? args[1] : "ranks.yml";
        
        sender.sendMessage(ChatColor.YELLOW + "Formatting " + fileName + "...");
        
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            boolean success = validationManager.formatYAML(fileName);
            
            getServer().getScheduler().runTask(this, () -> {
                if (success) {
                    sender.sendMessage(ChatColor.GREEN + "File formatted: " + fileName);
                } else {
                    sender.sendMessage(ChatColor.RED + "Failed to format file!");
                }
            });
        });
        
        return true;
    }
    
    // Getters for managers (for API access)
    public YAMLFileManager getYAMLFileManager() {
        return yamlFileManager;
    }
    
    public YAMLBackupManager getBackupManager() {
        return backupManager;
    }
    
    public YAMLValidationManager getValidationManager() {
        return validationManager;
    }
    
    public YAMLHistoryManager getHistoryManager() {
        return historyManager;
    }
    
    public FileConfiguration getRanksConfig() {
        return ranksConfig;
    }
    
    public TrackManager getTrackManager() {
        return trackManager;
    }
    
    public com.excrele.integrations.VaultIntegration getVaultIntegration() {
        return vaultIntegration;
    }
    
    /**
     * Handle rank purchase from GUI or command.
     */
    public void handleRankPurchase(Player player, String rankName) {
        FileConfiguration ranksConfig = getRanksConfig();
        
        if (!rankExists(rankName)) {
            player.sendMessage(ChatColor.RED + "Rank '" + rankName + "' does not exist!");
            return;
        }
        
        if (!ranksConfig.contains("ranks." + rankName + ".price")) {
            player.sendMessage(ChatColor.RED + "This rank is not available for purchase!");
            return;
        }
        
        double price = ranksConfig.getDouble("ranks." + rankName + ".price", 0.0);
        if (price <= 0) {
            player.sendMessage(ChatColor.RED + "This rank is not available for purchase!");
            return;
        }
        
        double balance = vaultIntegration.getBalance(player);
        if (balance < price) {
            player.sendMessage(ChatColor.RED + "Insufficient funds! You need " + 
                vaultIntegration.format(price) + " but only have " + vaultIntegration.format(balance));
            return;
        }
        
        // Check if player already has this rank or higher
        String currentRank = getPlayerRank(player.getUniqueId());
        if (currentRank.equals(rankName)) {
            player.sendMessage(ChatColor.YELLOW + "You already have this rank!");
            return;
        }
        
        // Withdraw money
        if (vaultIntegration.withdrawPlayer(player, price)) {
            // Assign rank
            setPlayerRank(player.getUniqueId(), rankName);
            loadPlayerPermissions(player);
            
            player.sendMessage(ChatColor.GREEN + "Successfully purchased rank '" + rankName + "' for " + 
                vaultIntegration.format(price) + "!");
            
            // Log the purchase
            if (loggingManager != null) {
                loggingManager.logRankChange(player.getName(), player.getUniqueId().toString(), 
                    currentRank, rankName, "System", "Purchased for " + vaultIntegration.format(price), 
                    YAMLLoggingManager.LogLevel.INFO);
            }
        } else {
            player.sendMessage(ChatColor.RED + "Failed to process payment!");
        }
    }
    
    // ========== New Feature Command Handlers ==========
    
    private boolean handleCreateRankCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank create <name> [prefix] [suffix]");
            return true;
        }
        
        String rankName = args[1];
        String prefix = args.length > 2 ? args[2] : "";
        String suffix = args.length > 3 ? args[3] : "";
        
        if (rankManager.createRank(rankName, prefix, suffix, new ArrayList<>(), new ArrayList<>())) {
            sender.sendMessage(ChatColor.GREEN + "Rank '" + rankName + "' created successfully!");
            ranksConfig = yamlFileManager.reloadConfig("ranks.yml");
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to create rank! (Rank may already exist)");
        }
        
        return true;
    }
    
    private boolean handleDeleteRankCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank delete <name> [confirm]");
            return true;
        }
        
        String rankName = args[1];
        
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            sender.sendMessage(ChatColor.YELLOW + "Warning: This will delete the rank '" + rankName + "'!");
            sender.sendMessage(ChatColor.YELLOW + "Type '/rank delete " + rankName + " confirm' to confirm.");
            return true;
        }
        
        if (rankManager.deleteRank(rankName)) {
            sender.sendMessage(ChatColor.GREEN + "Rank '" + rankName + "' deleted!");
            ranksConfig = yamlFileManager.reloadConfig("ranks.yml");
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to delete rank! (Rank may not exist)");
        }
        
        return true;
    }
    
    private boolean handleEditRankCommand(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank edit <name> <property> <value>");
            sender.sendMessage(ChatColor.YELLOW + "Properties: prefix, suffix, priority, track");
            return true;
        }
        
        String rankName = args[1];
        String property = args[2];
        String value = args[3];
        
        if (rankManager.editRank(rankName, property, value)) {
            sender.sendMessage(ChatColor.GREEN + "Rank '" + rankName + "' updated!");
            ranksConfig = yamlFileManager.reloadConfig("ranks.yml");
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to edit rank!");
        }
        
        return true;
    }
    
    private boolean handleCloneRankCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank clone <source> <target>");
            return true;
        }
        
        String sourceRank = args[1];
        String targetRank = args[2];
        
        if (rankManager.cloneRank(sourceRank, targetRank)) {
            sender.sendMessage(ChatColor.GREEN + "Rank '" + sourceRank + "' cloned to '" + targetRank + "'!");
            ranksConfig = yamlFileManager.reloadConfig("ranks.yml");
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to clone rank!");
        }
        
        return true;
    }
    
    private boolean handleTemporaryRankCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank temp <assign|list|cancel> [args]");
            return true;
        }
        
        switch (args[1].toLowerCase()) {
            case "assign":
            case "add":
                if (args.length < 5) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rank temp assign <player> <rank> <duration>");
                    sender.sendMessage(ChatColor.YELLOW + "Duration format: 1d, 2h, 30m, 1w");
                    return true;
                }
                
                String playerName = args[2];
                String rank = args[3];
                String durationStr = args[4];
                
                UUID playerUUID = null;
                Player onlinePlayer = getServer().getPlayer(playerName);
                if (onlinePlayer != null) {
                    playerUUID = onlinePlayer.getUniqueId();
                } else {
                    try {
                        playerUUID = UUID.fromString(playerName);
                    } catch (IllegalArgumentException e) {
                        OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(playerName);
                        if (offlinePlayer.hasPlayedBefore()) {
                            playerUUID = offlinePlayer.getUniqueId();
                        } else {
                            sender.sendMessage(ChatColor.RED + "Player not found!");
                            return true;
                        }
                    }
                }
                
                long duration = temporaryRankManager.parseDuration(durationStr);
                if (duration <= 0) {
                    sender.sendMessage(ChatColor.RED + "Invalid duration format!");
                    return true;
                }
                
                // Store current rank and assign temp rank
                ranksConfig.set("players." + playerUUID + ".rank", rank);
                saveConfigFileSync();
                
                if (temporaryRankManager.assignTemporaryRank(playerUUID, rank, duration)) {
                    sender.sendMessage(ChatColor.GREEN + "Temporary rank assigned to " + playerName + " for " + durationStr);
                    if (onlinePlayer != null) {
                        loadPlayerPermissions(onlinePlayer);
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "Failed to assign temporary rank!");
                }
                return true;
                
            case "list":
                Map<UUID, TemporaryRankManager.TemporaryRankInfo> tempRanks = temporaryRankManager.getAllTemporaryRanks();
                if (tempRanks.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "No active temporary ranks.");
                } else {
                    sender.sendMessage(ChatColor.GOLD + "=== Active Temporary Ranks ===");
                    for (Map.Entry<UUID, TemporaryRankManager.TemporaryRankInfo> entry : tempRanks.entrySet()) {
                        UUID tempPlayerUUID = entry.getKey();
                        TemporaryRankManager.TemporaryRankInfo info = entry.getValue();
                        
                        String tempPlayerName = tempPlayerUUID.toString();
                        Player tempOnlinePlayer = getServer().getPlayer(tempPlayerUUID);
                        if (tempOnlinePlayer != null) {
                            tempPlayerName = tempOnlinePlayer.getName();
                        } else {
                            OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(tempPlayerUUID);
                            if (offlinePlayer.hasPlayedBefore() && offlinePlayer.getName() != null) {
                                tempPlayerName = offlinePlayer.getName();
                            }
                        }
                        
                        long remaining = info.getRemainingTime();
                        long days = remaining / (24L * 60 * 60 * 1000);
                        long hours = (remaining % (24L * 60 * 60 * 1000)) / (60 * 60 * 1000);
                        long minutes = (remaining % (60 * 60 * 1000)) / (60 * 1000);
                        
                        String timeStr = "";
                        if (days > 0) timeStr += days + "d ";
                        if (hours > 0) timeStr += hours + "h ";
                        if (minutes > 0) timeStr += minutes + "m ";
                        if (timeStr.isEmpty()) timeStr = "< 1m";
                        
                        sender.sendMessage(ChatColor.YELLOW + tempPlayerName + ": " + 
                            ChatColor.GREEN + info.getRank() + ChatColor.YELLOW + 
                            " (from " + info.getOriginalRank() + ", expires in " + timeStr.trim() + ")");
                    }
                }
                return true;
                
            case "cancel":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rank temp cancel <player>");
                    return true;
                }
                
                playerName = args[2];
                onlinePlayer = getServer().getPlayer(playerName);
                if (onlinePlayer != null) {
                    playerUUID = onlinePlayer.getUniqueId();
                } else {
                    try {
                        playerUUID = UUID.fromString(playerName);
                    } catch (IllegalArgumentException e) {
                        OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(playerName);
                        if (offlinePlayer.hasPlayedBefore()) {
                            playerUUID = offlinePlayer.getUniqueId();
                        } else {
                            sender.sendMessage(ChatColor.RED + "Player not found!");
                            return true;
                        }
                    }
                }
                
                if (temporaryRankManager.cancelTemporaryRank(playerUUID)) {
                    sender.sendMessage(ChatColor.GREEN + "Temporary rank cancelled for " + playerName);
                    if (onlinePlayer != null) {
                        loadPlayerPermissions(onlinePlayer);
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "Player doesn't have a temporary rank!");
                }
                return true;
                
            default:
                sender.sendMessage(ChatColor.RED + "Unknown temp subcommand!");
                return true;
        }
    }
    
    private boolean handleTrackCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank track <create|list|delete> [args]");
            return true;
        }
        
        switch (args[1].toLowerCase()) {
            case "create":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rank track create <name> <rank1> <rank2> ...");
                    return true;
                }
                
                String trackName = args[2];
                List<String> ranks = new ArrayList<>();
                for (int i = 3; i < args.length; i++) {
                    ranks.add(args[i]);
                }
                
                if (trackManager.createTrack(trackName, ranks)) {
                    sender.sendMessage(ChatColor.GREEN + "Track '" + trackName + "' created!");
                } else {
                    sender.sendMessage(ChatColor.RED + "Failed to create track! (Track may already exist)");
                }
                return true;
                
            case "list":
                List<String> tracks = trackManager.getAllTracks();
                if (tracks.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "No tracks configured.");
                } else {
                    sender.sendMessage(ChatColor.GOLD + "=== Progression Tracks ===");
                    for (String track : tracks) {
                        List<String> trackRanks = trackManager.getTrack(track);
                        sender.sendMessage(ChatColor.YELLOW + track + ": " + String.join(" → ", trackRanks));
                    }
                }
                return true;
                
            case "delete":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rank track delete <name>");
                    return true;
                }
                
                trackName = args[2];
                if (trackManager.deleteTrack(trackName)) {
                    sender.sendMessage(ChatColor.GREEN + "Track '" + trackName + "' deleted!");
                } else {
                    sender.sendMessage(ChatColor.RED + "Failed to delete track!");
                }
                return true;
                
            default:
                sender.sendMessage(ChatColor.RED + "Unknown track subcommand!");
                return true;
        }
    }
    
    private boolean handlePermissionCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank permission <add|remove|list|clear> <rank> [permission]");
            return true;
        }
        
        String action = args[1].toLowerCase();
        String rankName = args[2];
        
        switch (action) {
            case "add":
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rank permission add <rank> <permission>");
                    return true;
                }
                String permission = args[3];
                if (rankManager.addPermission(rankName, permission)) {
                    sender.sendMessage(ChatColor.GREEN + "Permission added to rank '" + rankName + "'!");
                    ranksConfig = yamlFileManager.reloadConfig("ranks.yml");
                } else {
                    sender.sendMessage(ChatColor.RED + "Failed to add permission!");
                }
                return true;
                
            case "remove":
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rank permission remove <rank> <permission>");
                    return true;
                }
                permission = args[3];
                if (rankManager.removePermission(rankName, permission)) {
                    sender.sendMessage(ChatColor.GREEN + "Permission removed from rank '" + rankName + "'!");
                    ranksConfig = yamlFileManager.reloadConfig("ranks.yml");
                } else {
                    sender.sendMessage(ChatColor.RED + "Failed to remove permission!");
                }
                return true;
                
            case "list":
                List<String> permissions = ranksConfig.getStringList("ranks." + rankName + ".permissions");
                if (permissions.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "No permissions for rank '" + rankName + "'");
                } else {
                    sender.sendMessage(ChatColor.GOLD + "=== Permissions for " + rankName + " ===");
                    for (String perm : permissions) {
                        if (perm.startsWith("-")) {
                            sender.sendMessage(ChatColor.RED + "  - " + perm);
                        } else {
                            sender.sendMessage(ChatColor.GREEN + "  + " + perm);
                        }
                    }
                }
                return true;
                
            case "clear":
                ranksConfig.set("ranks." + rankName + ".permissions", new ArrayList<>());
                if (saveConfigFileSync()) {
                    sender.sendMessage(ChatColor.GREEN + "All permissions cleared for rank '" + rankName + "'!");
                } else {
                    sender.sendMessage(ChatColor.RED + "Failed to clear permissions!");
                }
                return true;
                
            default:
                sender.sendMessage(ChatColor.RED + "Unknown permission action!");
                return true;
        }
    }
    
    private boolean handleInheritanceCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank inherit <add|remove|list> <rank> [parent]");
            return true;
        }
        
        String action = args[1].toLowerCase();
        String rankName = args[2];
        
        switch (action) {
            case "add":
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rank inherit add <rank> <parent>");
                    return true;
                }
                String parentRank = args[3];
                if (rankManager.addInheritance(rankName, parentRank)) {
                    sender.sendMessage(ChatColor.GREEN + "Inheritance added to rank '" + rankName + "'!");
                    ranksConfig = yamlFileManager.reloadConfig("ranks.yml");
                } else {
                    sender.sendMessage(ChatColor.RED + "Failed to add inheritance!");
                }
                return true;
                
            case "remove":
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rank inherit remove <rank> <parent>");
                    return true;
                }
                parentRank = args[3];
                if (rankManager.removeInheritance(rankName, parentRank)) {
                    sender.sendMessage(ChatColor.GREEN + "Inheritance removed from rank '" + rankName + "'!");
                    ranksConfig = yamlFileManager.reloadConfig("ranks.yml");
                } else {
                    sender.sendMessage(ChatColor.RED + "Failed to remove inheritance!");
                }
                return true;
                
            case "list":
                List<String> inheritance = ranksConfig.getStringList("ranks." + rankName + ".inheritance");
                if (inheritance.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "Rank '" + rankName + "' doesn't inherit from any ranks");
                } else {
                    sender.sendMessage(ChatColor.GOLD + "=== Inheritance for " + rankName + " ===");
                    for (String parent : inheritance) {
                        sender.sendMessage(ChatColor.YELLOW + "  - " + parent);
                    }
                }
                return true;
                
            default:
                sender.sendMessage(ChatColor.RED + "Unknown inherit action!");
                return true;
        }
    }
    
    private boolean handleFilesCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank files <split|merge>");
            return true;
        }
        
        switch (args[1].toLowerCase()) {
            case "split":
                sender.sendMessage(ChatColor.YELLOW + "Splitting files...");
                getServer().getScheduler().runTaskAsynchronously(this, () -> {
                    boolean success = splitFiles();
                    getServer().getScheduler().runTask(this, () -> {
                        if (success) {
                            sender.sendMessage(ChatColor.GREEN + "Files split successfully! Using split file mode.");
                            sender.sendMessage(ChatColor.YELLOW + "Created: ranks.yml, players.yml, tracks.yml");
                        } else {
                            sender.sendMessage(ChatColor.RED + "Failed to split files!");
                        }
                    });
                });
                return true;
                
            case "merge":
                sender.sendMessage(ChatColor.YELLOW + "Merging files...");
                getServer().getScheduler().runTaskAsynchronously(this, () -> {
                    boolean success = mergeFiles();
                    getServer().getScheduler().runTask(this, () -> {
                        if (success) {
                            sender.sendMessage(ChatColor.GREEN + "Files merged successfully! Using single file mode.");
                            ranksConfig = yamlFileManager.reloadConfig("ranks.yml");
                        } else {
                            sender.sendMessage(ChatColor.RED + "Failed to merge files!");
                        }
                    });
                });
                return true;
                
            default:
                sender.sendMessage(ChatColor.RED + "Unknown files action!");
                return true;
        }
    }
    
    private boolean handleLogsCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank logs <view|export|list> [args]");
            return true;
        }
        
        switch (args[1].toLowerCase()) {
            case "view":
                String playerName = args.length > 2 ? args[2] : null;
                if (playerName != null) {
                    // View logs for specific player
                    Player onlinePlayer = getServer().getPlayer(playerName);
                    UUID finalPlayerUUID;
                    if (onlinePlayer != null) {
                        finalPlayerUUID = onlinePlayer.getUniqueId();
                    } else {
                        try {
                            finalPlayerUUID = UUID.fromString(playerName);
                        } catch (IllegalArgumentException e) {
                            OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(playerName);
                            if (offlinePlayer.hasPlayedBefore()) {
                                finalPlayerUUID = offlinePlayer.getUniqueId();
                            } else {
                                sender.sendMessage(ChatColor.RED + "Player not found!");
                                return true;
                            }
                        }
                    }
                    final UUID finalPlayerUUIDFinal = finalPlayerUUID;
                    
                    final String finalPlayerName = playerName;
                    // Read log files and filter by player
                    sender.sendMessage(ChatColor.GOLD + "=== Logs for " + finalPlayerName + " ===");
                    getServer().getScheduler().runTaskAsynchronously(this, () -> {
                        File[] logFiles = loggingManager.getAllLogFiles();
                        List<String> matchingLines = new ArrayList<>();
                        
                        for (File logFile : logFiles) {
                            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                                    new java.io.FileReader(logFile))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    if (line.contains(finalPlayerUUIDFinal.toString()) || 
                                        (finalPlayerName != null && line.contains(finalPlayerName))) {
                                        matchingLines.add(line);
                                    }
                                }
                            } catch (IOException e) {
                                // Skip file
                            }
                        }
                        
                        getServer().getScheduler().runTask(this, () -> {
                            if (matchingLines.isEmpty()) {
                                sender.sendMessage(ChatColor.YELLOW + "No logs found for this player.");
                            } else {
                                int maxLines = 50;
                                int start = Math.max(0, matchingLines.size() - maxLines);
                                for (int i = start; i < matchingLines.size(); i++) {
                                    sender.sendMessage(ChatColor.WHITE + matchingLines.get(i));
                                }
                                if (matchingLines.size() > maxLines) {
                                    sender.sendMessage(ChatColor.YELLOW + "... and " + 
                                        (matchingLines.size() - maxLines) + " more entries");
                                }
                            }
                        });
                    });
                } else {
                    // View recent logs
                    sender.sendMessage(ChatColor.GOLD + "=== Recent Logs ===");
                    getServer().getScheduler().runTaskAsynchronously(this, () -> {
                        File[] logFiles = loggingManager.getAllLogFiles();
                        if (logFiles == null || logFiles.length == 0) {
                            getServer().getScheduler().runTask(this, () -> {
                                sender.sendMessage(ChatColor.YELLOW + "No log files found.");
                            });
                            return;
                        }
                        
                        // Get most recent log file
                        File currentLog = logFiles[logFiles.length - 1];
                        List<String> lines = new ArrayList<>();
                        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                                new java.io.FileReader(currentLog))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                lines.add(line);
                            }
                        } catch (IOException e) {
                            // Error reading
                        }
                        
                        getServer().getScheduler().runTask(this, () -> {
                            if (lines.isEmpty()) {
                                sender.sendMessage(ChatColor.YELLOW + "No log entries found.");
                            } else {
                                int maxLines = 20;
                                int start = Math.max(0, lines.size() - maxLines);
                                for (int i = start; i < lines.size(); i++) {
                                    sender.sendMessage(ChatColor.WHITE + lines.get(i));
                                }
                                if (lines.size() > maxLines) {
                                    sender.sendMessage(ChatColor.YELLOW + "... and " + 
                                        (lines.size() - maxLines) + " more entries");
                                }
                            }
                        });
                    });
                }
                return true;
                
            case "export":
                String filename = args.length > 2 ? args[2] : 
                    "logs_export_" + System.currentTimeMillis() + ".txt";
                String startDate = args.length > 3 ? args[3] : null;
                String endDate = args.length > 4 ? args[4] : null;
                
                File exportFile = new File(getDataFolder(), "exports/" + filename);
                sender.sendMessage(ChatColor.YELLOW + "Exporting logs...");
                
                getServer().getScheduler().runTaskAsynchronously(this, () -> {
                    boolean success = loggingManager.exportLogs(exportFile, startDate, endDate);
                    getServer().getScheduler().runTask(this, () -> {
                        if (success) {
                            sender.sendMessage(ChatColor.GREEN + "Logs exported to: " + filename);
                        } else {
                            sender.sendMessage(ChatColor.RED + "Failed to export logs!");
                        }
                    });
                });
                return true;
                
            case "list":
                File[] logFiles = loggingManager.getAllLogFiles();
                if (logFiles == null || logFiles.length == 0) {
                    sender.sendMessage(ChatColor.YELLOW + "No log files found.");
                } else {
                    sender.sendMessage(ChatColor.GOLD + "=== Log Files ===");
                    for (File logFile : logFiles) {
                        long size = logFile.length();
                        String sizeStr = size < 1024 ? size + " B" : 
                            size < 1024 * 1024 ? (size / 1024) + " KB" : 
                            (size / (1024 * 1024)) + " MB";
                        sender.sendMessage(ChatColor.YELLOW + logFile.getName() + 
                            ChatColor.GRAY + " (" + sizeStr + ")");
                    }
                }
                return true;
                
            default:
                sender.sendMessage(ChatColor.RED + "Unknown logs action!");
                return true;
        }
    }
    
    private boolean splitFiles() {
        try {
            FileConfiguration ranksConfig = yamlFileManager.getConfig("ranks.yml");
            
            // Create separate files
            FileConfiguration ranksOnly = new org.bukkit.configuration.file.YamlConfiguration();
            FileConfiguration playersOnly = new org.bukkit.configuration.file.YamlConfiguration();
            FileConfiguration tracksOnly = new org.bukkit.configuration.file.YamlConfiguration();
            
            // Copy ranks section
            if (ranksConfig.contains("ranks")) {
                ranksOnly.set("ranks", ranksConfig.getConfigurationSection("ranks"));
            }
            
            // Copy players section
            if (ranksConfig.contains("players")) {
                playersOnly.set("players", ranksConfig.getConfigurationSection("players"));
            }
            
            // Copy tracks section
            if (ranksConfig.contains("progression-tracks")) {
                tracksOnly.set("progression-tracks", ranksConfig.getConfigurationSection("progression-tracks"));
            }
            
            // Save separate files
            yamlFileManager.saveConfigSync("ranks.yml", ranksOnly);
            yamlFileManager.saveConfigSync("players.yml", playersOnly);
            yamlFileManager.saveConfigSync("tracks.yml", tracksOnly);
            
            // Enable split file mode
            yamlFileManager.setUseSplitFiles(true);
            
            return true;
        } catch (Exception e) {
            getLogger().severe("Failed to split files: " + e.getMessage());
            return false;
        }
    }
    
    private boolean mergeFiles() {
        try {
            FileConfiguration merged = new org.bukkit.configuration.file.YamlConfiguration();
            
            // Load from separate files
            FileConfiguration ranksConfig = yamlFileManager.getConfig("ranks.yml");
            FileConfiguration playersConfig = yamlFileManager.getConfig("players.yml");
            FileConfiguration tracksConfig = yamlFileManager.getConfig("tracks.yml");
            
            // Merge into one
            if (ranksConfig.contains("ranks")) {
                merged.set("ranks", ranksConfig.getConfigurationSection("ranks"));
            }
            if (playersConfig.contains("players")) {
                merged.set("players", playersConfig.getConfigurationSection("players"));
            }
            if (tracksConfig.contains("progression-tracks")) {
                merged.set("progression-tracks", tracksConfig.getConfigurationSection("progression-tracks"));
            }
            
            // Save merged file
            yamlFileManager.saveConfigSync("ranks.yml", merged);
            
            // Disable split file mode
            yamlFileManager.setUseSplitFiles(false);
            
            return true;
        } catch (Exception e) {
            getLogger().severe("Failed to merge files: " + e.getMessage());
            return false;
        }
    }
    
    private boolean handleCommentCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank comment <add|remove|view> <rank> [comment]");
            return true;
        }
        
        String action = args[1].toLowerCase();
        String rankName = args[2];
        
        if (!rankExists(rankName)) {
            sender.sendMessage(ChatColor.RED + "Rank '" + rankName + "' does not exist!");
            return true;
        }
        
        switch (action) {
            case "add":
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rank comment add <rank> <comment>");
                    return true;
                }
                StringBuilder commentBuilder = new StringBuilder();
                for (int i = 3; i < args.length; i++) {
                    if (i > 3) commentBuilder.append(" ");
                    commentBuilder.append(args[i]);
                }
                String comment = commentBuilder.toString();
                
                if (commentManager.addComment(rankName, comment)) {
                    sender.sendMessage(ChatColor.GREEN + "Comment added to rank '" + rankName + "'!");
                } else {
                    sender.sendMessage(ChatColor.RED + "Failed to add comment!");
                }
                return true;
                
            case "remove":
                if (commentManager.removeComment(rankName)) {
                    sender.sendMessage(ChatColor.GREEN + "Comment removed from rank '" + rankName + "'!");
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "No comment found for rank '" + rankName + "'");
                }
                return true;
                
            case "view":
                String existingComment = commentManager.getComment(rankName);
                if (existingComment != null && !existingComment.isEmpty()) {
                    sender.sendMessage(ChatColor.GOLD + "=== Comment for " + rankName + " ===");
                    sender.sendMessage(ChatColor.YELLOW + existingComment);
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "No comment found for rank '" + rankName + "'");
                }
                return true;
                
            default:
                sender.sendMessage(ChatColor.RED + "Unknown comment action! Use: add, remove, or view");
                return true;
        }
    }
    
    private boolean handleSetPriorityCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank setpriority <rank> <priority>");
            sender.sendMessage(ChatColor.YELLOW + "Priority: Higher number = more important (affects tab list order)");
            return true;
        }
        
        String rankName = args[1];
        String priorityStr = args[2];
        
        if (!rankExists(rankName)) {
            sender.sendMessage(ChatColor.RED + "Rank '" + rankName + "' does not exist!");
            return true;
        }
        
        try {
            int priority = Integer.parseInt(priorityStr);
            
            if (rankManager.editRank(rankName, "priority", String.valueOf(priority))) {
                sender.sendMessage(ChatColor.GREEN + "Priority set to " + priority + " for rank '" + rankName + "'!");
                ranksConfig = yamlFileManager.reloadConfig("ranks.yml");
                
                // Update tab list for all online players
                sortTabListByPriority();
            } else {
                sender.sendMessage(ChatColor.RED + "Failed to set priority!");
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid priority! Must be a number.");
        }
        
        return true;
    }
    
    // ========== Additional Feature Command Handlers ==========
    
    private boolean handleBulkCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank bulk <add|promote|demote> <rank/players...>");
            sender.sendMessage(ChatColor.YELLOW + "Example: /rank bulk add vip Player1 Player2 Player3");
            return true;
        }
        
        String action = args[1].toLowerCase();
        
        // Check if last argument is a file
        String lastArg = args[args.length - 1];
        final List<String> finalPlayerNames;
        if (lastArg.endsWith(".txt") || lastArg.endsWith(".list")) {
            File playerFile = new File(getDataFolder(), "lists/" + lastArg);
            if (playerFile.exists()) {
                finalPlayerNames = bulkOperationsManager.loadPlayerListFromFile(playerFile);
                sender.sendMessage(ChatColor.YELLOW + "Loaded " + finalPlayerNames.size() + " players from file.");
            } else {
                sender.sendMessage(ChatColor.RED + "Player list file not found: " + lastArg);
                return true;
            }
        } else {
            // Get players from arguments
            finalPlayerNames = new ArrayList<>();
            int startIdx = action.equals("add") ? 3 : 2;
            for (int i = startIdx; i < args.length; i++) {
                finalPlayerNames.add(args[i]);
            }
        }
        
        if (finalPlayerNames.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No players specified!");
            return true;
        }
        
        sender.sendMessage(ChatColor.YELLOW + "Processing " + finalPlayerNames.size() + " player(s)...");
        
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            com.excrele.managers.BulkOperationsManager.BulkResult result;
            
            switch (action) {
                case "add":
                    String rank = args[2];
                    result = bulkOperationsManager.bulkAddRank(sender, rank, finalPlayerNames);
                    break;
                case "promote":
                    result = bulkOperationsManager.bulkPromote(sender, finalPlayerNames, trackManager);
                    break;
                case "demote":
                    result = bulkOperationsManager.bulkDemote(sender, finalPlayerNames, trackManager);
                    break;
                default:
                    getServer().getScheduler().runTask(this, () -> {
                        sender.sendMessage(ChatColor.RED + "Unknown bulk action! Use: add, promote, or demote");
                    });
                    return;
            }
            
            getServer().getScheduler().runTask(this, () -> {
                sender.sendMessage(ChatColor.GOLD + "=== Bulk Operation Results ===");
                sender.sendMessage(ChatColor.GREEN + "Success: " + result.getSuccesses().size());
                if (!result.getSuccesses().isEmpty() && result.getSuccesses().size() <= 10) {
                    for (String success : result.getSuccesses()) {
                        sender.sendMessage(ChatColor.GREEN + "  ✓ " + success);
                    }
                } else if (result.getSuccesses().size() > 10) {
                    sender.sendMessage(ChatColor.GREEN + "  (Showing first 10)");
                    for (int i = 0; i < 10; i++) {
                        sender.sendMessage(ChatColor.GREEN + "  ✓ " + result.getSuccesses().get(i));
                    }
                }
                
                if (!result.getSkipped().isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "Skipped: " + result.getSkipped().size());
                    for (String skip : result.getSkipped()) {
                        sender.sendMessage(ChatColor.YELLOW + "  - " + skip);
                    }
                }
                
                if (!result.getErrors().isEmpty()) {
                    sender.sendMessage(ChatColor.RED + "Errors: " + result.getErrors().size());
                    for (String error : result.getErrors()) {
                        sender.sendMessage(ChatColor.RED + "  ✗ " + error);
                    }
                }
            });
        });
        
        return true;
    }
    
    private boolean handleHelpCommand(CommandSender sender, String[] args) {
        if (args.length > 1) {
            // Detailed help for specific command
            String command = args[1].toLowerCase();
            showCommandHelp(sender, command);
        } else {
            // General help menu
            sender.sendMessage(ChatColor.GOLD + "=== ExcrelePerms Help ===");
            sender.sendMessage(ChatColor.YELLOW + "Use /rank help <command> for detailed help");
            sender.sendMessage("");
            sender.sendMessage(ChatColor.GOLD + "Basic Commands:");
            sender.sendMessage(ChatColor.WHITE + "  /rank list" + ChatColor.GRAY + " - List all ranks");
            sender.sendMessage(ChatColor.WHITE + "  /rank info <rank>" + ChatColor.GRAY + " - View rank details");
            sender.sendMessage(ChatColor.WHITE + "  /rank check <player>" + ChatColor.GRAY + " - Check player's rank");
            sender.sendMessage("");
            sender.sendMessage(ChatColor.GOLD + "Rank Management:");
            sender.sendMessage(ChatColor.WHITE + "  /rank add <player> <rank>" + ChatColor.GRAY + " - Assign rank");
            sender.sendMessage(ChatColor.WHITE + "  /rank promote <player>" + ChatColor.GRAY + " - Promote player");
            sender.sendMessage(ChatColor.WHITE + "  /rank demote <player>" + ChatColor.GRAY + " - Demote player");
            sender.sendMessage(ChatColor.WHITE + "  /rank create <name>" + ChatColor.GRAY + " - Create rank");
            sender.sendMessage(ChatColor.WHITE + "  /rank delete <name>" + ChatColor.GRAY + " - Delete rank");
            sender.sendMessage("");
            sender.sendMessage(ChatColor.GOLD + "Advanced Commands:");
            sender.sendMessage(ChatColor.WHITE + "  /rank bulk <action> <args>" + ChatColor.GRAY + " - Bulk operations");
            sender.sendMessage(ChatColor.WHITE + "  /rank temp <action> <args>" + ChatColor.GRAY + " - Temporary ranks");
            sender.sendMessage(ChatColor.WHITE + "  /rank track <action>" + ChatColor.GRAY + " - Manage tracks");
            sender.sendMessage(ChatColor.WHITE + "  /rank backup <action>" + ChatColor.GRAY + " - Backup management");
            sender.sendMessage("");
            sender.sendMessage(ChatColor.GRAY + "Type /rank help <command> for more details");
        }
        return true;
    }
    
    private void showCommandHelp(CommandSender sender, String command) {
        switch (command.toLowerCase()) {
            case "add":
            case "assign":
                sender.sendMessage(ChatColor.GOLD + "=== /rank add ===");
                sender.sendMessage(ChatColor.YELLOW + "Usage: /rank add <player> <rank>");
                sender.sendMessage(ChatColor.WHITE + "Assigns a rank to a player.");
                sender.sendMessage(ChatColor.GRAY + "Example: /rank add PlayerName vip");
                break;
            case "promote":
                sender.sendMessage(ChatColor.GOLD + "=== /rank promote ===");
                sender.sendMessage(ChatColor.YELLOW + "Usage: /rank promote <player>");
                sender.sendMessage(ChatColor.WHITE + "Promotes a player to the next rank in their track.");
                break;
            case "bulk":
                sender.sendMessage(ChatColor.GOLD + "=== /rank bulk ===");
                sender.sendMessage(ChatColor.YELLOW + "Usage: /rank bulk <add|promote|demote> <args>");
                sender.sendMessage(ChatColor.WHITE + "Perform operations on multiple players at once.");
                sender.sendMessage(ChatColor.GRAY + "Examples:");
                sender.sendMessage(ChatColor.GRAY + "  /rank bulk add vip Player1 Player2 Player3");
                sender.sendMessage(ChatColor.GRAY + "  /rank bulk promote Player1 Player2 Player3");
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Help not available for: " + command);
        }
    }
    
    private boolean handleTagCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank tag <add|remove|list> <rank> [tag]");
            return true;
        }
        
        String action = args[1].toLowerCase();
        String rankName = args[2];
        
        if (!rankExists(rankName)) {
            sender.sendMessage(ChatColor.RED + "Rank '" + rankName + "' does not exist!");
            return true;
        }
        
        FileConfiguration ranksConfig = yamlFileManager.getConfig("ranks.yml");
        
        switch (action) {
            case "add":
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rank tag add <rank> <tag>");
                    return true;
                }
                String tag = args[3];
                List<String> tags = ranksConfig.getStringList("ranks." + rankName + ".tags");
                if (!tags.contains(tag)) {
                    tags.add(tag);
                    ranksConfig.set("ranks." + rankName + ".tags", tags);
                    yamlFileManager.saveConfigSync("ranks.yml", ranksConfig);
                    sender.sendMessage(ChatColor.GREEN + "Tag '" + tag + "' added to rank '" + rankName + "'!");
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "Tag already exists!");
                }
                return true;
                
            case "remove":
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rank tag remove <rank> <tag>");
                    return true;
                }
                tag = args[3];
                tags = ranksConfig.getStringList("ranks." + rankName + ".tags");
                if (tags.remove(tag)) {
                    ranksConfig.set("ranks." + rankName + ".tags", tags);
                    yamlFileManager.saveConfigSync("ranks.yml", ranksConfig);
                    sender.sendMessage(ChatColor.GREEN + "Tag '" + tag + "' removed from rank '" + rankName + "'!");
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "Tag not found!");
                }
                return true;
                
            case "list":
                tags = ranksConfig.getStringList("ranks." + rankName + ".tags");
                if (tags.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "No tags for rank '" + rankName + "'");
                } else {
                    sender.sendMessage(ChatColor.GOLD + "=== Tags for " + rankName + " ===");
                    for (String t : tags) {
                        sender.sendMessage(ChatColor.YELLOW + "  - " + t);
                    }
                }
                return true;
                
            default:
                sender.sendMessage(ChatColor.RED + "Unknown tag action!");
                return true;
        }
    }
    
    private boolean handleColorCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank setcolor <rank> <color>");
            sender.sendMessage(ChatColor.YELLOW + "Color formats: &c (Minecraft color code), #FF0000 (hex), or color name");
            return true;
        }
        
        String rankName = args[1];
        String color = args[2];
        
        if (!rankExists(rankName)) {
            sender.sendMessage(ChatColor.RED + "Rank '" + rankName + "' does not exist!");
            return true;
        }
        
        // Convert hex to Minecraft color if needed
        if (color.startsWith("#")) {
            // Simple hex to color code conversion (basic implementation)
            color = convertHexToColorCode(color);
        }
        
        FileConfiguration ranksConfig = yamlFileManager.getConfig("ranks.yml");
        ranksConfig.set("ranks." + rankName + ".color", color);
        yamlFileManager.saveConfigSync("ranks.yml", ranksConfig);
        
        sender.sendMessage(ChatColor.GREEN + "Color set for rank '" + rankName + "'!");
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', color + "Preview: " + color + rankName + ChatColor.RESET));
        
        return true;
    }
    
    private String convertHexToColorCode(String hex) {
        // Basic hex to Minecraft color code conversion
        // This is a simplified version - full implementation would need color mapping
        hex = hex.replace("#", "");
        try {
            int rgb = Integer.parseInt(hex, 16);
            // Map to nearest Minecraft color (simplified)
            return "&" + getNearestColorCode(rgb);
        } catch (NumberFormatException e) {
            return hex; // Return as-is if invalid
        }
    }
    
    private String getNearestColorCode(int rgb) {
        // Simplified color mapping - returns a basic color code
        // Full implementation would calculate nearest Minecraft color
        return "f"; // Default to white
    }
    
    private boolean handleRequirementsCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank requirements <rank> [set <type> <value>]");
            return true;
        }
        
        String rankName = args[1];
        if (!rankExists(rankName)) {
            sender.sendMessage(ChatColor.RED + "Rank '" + rankName + "' does not exist!");
            return true;
        }
        
        FileConfiguration ranksConfig = yamlFileManager.getConfig("ranks.yml");
        
        if (args.length >= 4 && args[2].equalsIgnoreCase("set")) {
            String type = args[3].toLowerCase();
            String value = args.length > 4 ? args[4] : "";
            
            switch (type) {
                case "playtime":
                case "money":
                case "permission":
                    ranksConfig.set("ranks." + rankName + ".requirements." + type, value);
                    yamlFileManager.saveConfigSync("ranks.yml", ranksConfig);
                    sender.sendMessage(ChatColor.GREEN + "Requirement set!");
                    break;
                default:
                    sender.sendMessage(ChatColor.RED + "Unknown requirement type! Use: playtime, money, or permission");
            }
        } else {
            // Show requirements
            sender.sendMessage(ChatColor.GOLD + "=== Requirements for " + rankName + " ===");
            if (ranksConfig.contains("ranks." + rankName + ".requirements")) {
                for (String key : ranksConfig.getConfigurationSection("ranks." + rankName + ".requirements").getKeys(false)) {
                    String value = ranksConfig.getString("ranks." + rankName + ".requirements." + key);
                    sender.sendMessage(ChatColor.YELLOW + key + ": " + ChatColor.WHITE + value);
                }
            } else {
                sender.sendMessage(ChatColor.YELLOW + "No requirements set.");
            }
        }
        
        return true;
    }
    
    private boolean handleExpiryCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank setexpiry <rank> <duration>");
            sender.sendMessage(ChatColor.YELLOW + "Duration format: 1d, 2h, 30m, 1w");
            return true;
        }
        
        String rankName = args[1];
        String durationStr = args[2];
        
        if (!rankExists(rankName)) {
            sender.sendMessage(ChatColor.RED + "Rank '" + rankName + "' does not exist!");
            return true;
        }
        
        long duration = temporaryRankManager.parseDuration(durationStr);
        if (duration <= 0) {
            sender.sendMessage(ChatColor.RED + "Invalid duration format!");
            return true;
        }
        
        FileConfiguration ranksConfig = yamlFileManager.getConfig("ranks.yml");
        ranksConfig.set("ranks." + rankName + ".expiry-duration", durationStr);
        yamlFileManager.saveConfigSync("ranks.yml", ranksConfig);
        
        sender.sendMessage(ChatColor.GREEN + "Expiry duration set for rank '" + rankName + "'!");
        
        return true;
    }
    
    private boolean handleBatchCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank batch <start|commit|cancel>");
            return true;
        }
        
        sender.sendMessage(ChatColor.YELLOW + "Batch mode is a planned feature. For now, use bulk operations.");
        return true;
    }
    
    private boolean handleCacheCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank cache <clear|stats>");
            return true;
        }
        
        switch (args[1].toLowerCase()) {
            case "clear":
                yamlFileManager.clearAllCaches();
                sender.sendMessage(ChatColor.GREEN + "Cache cleared!");
                return true;
            case "stats":
                sender.sendMessage(ChatColor.GOLD + "=== Cache Statistics ===");
                sender.sendMessage(ChatColor.YELLOW + "Cached files: " + yamlFileManager.getConfig("ranks.yml").getKeys(false).size());
                return true;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown cache action!");
                return true;
        }
    }
    
    private boolean handleBuyCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length < 2) {
            // Open purchase GUI
            rankGUI.openRankPurchaseGUI(player);
            return true;
        }
        
        String rankName = args[1];
        handleRankPurchase(player, rankName);
        return true;
    }
    
    @SuppressWarnings("unused")
    private boolean handleMigrateCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank migrate <plugin> <file>");
            sender.sendMessage(ChatColor.YELLOW + "Supported plugins: luckperms, groupmanager, permissionsex");
            return true;
        }
        
        String pluginType = args[1];
        String fileName = args[2];
        File sourceFile = new File(getDataFolder(), fileName);
        
        if (!sourceFile.exists()) {
            sender.sendMessage(ChatColor.RED + "File not found: " + fileName);
            return true;
        }
        
        sender.sendMessage(ChatColor.YELLOW + "Migrating from " + pluginType + "...");
        com.excrele.managers.MigrationManager.MigrationResult result = 
            migrationManager.migrateFromYAML(sourceFile, pluginType);
        
        if (result.isSuccess()) {
            sender.sendMessage(ChatColor.GREEN + "Migration completed!");
            sender.sendMessage(ChatColor.YELLOW + "Migrated: " + result.getMigrated().size() + " items");
            for (String item : result.getMigrated()) {
                sender.sendMessage(ChatColor.GRAY + "  - " + item);
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Migration failed!");
            for (String error : result.getErrors()) {
                sender.sendMessage(ChatColor.RED + "  - " + error);
            }
        }
        
        return true;
    }
    
    @SuppressWarnings("unused")
    private boolean handleWorldCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank world <add|remove|get> <player> <rank> [world]");
            return true;
        }
        
        String action = args[1].toLowerCase();
        String playerName = args[2];
        
        UUID playerUUID = null;
        Player onlinePlayer = getServer().getPlayer(playerName);
        if (onlinePlayer != null) {
            playerUUID = onlinePlayer.getUniqueId();
        } else {
            OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(playerName);
            if (offlinePlayer.hasPlayedBefore()) {
                playerUUID = offlinePlayer.getUniqueId();
            } else {
                sender.sendMessage(ChatColor.RED + "Player not found!");
                return true;
            }
        }
        
        switch (action) {
            case "add":
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rank world add <player> <rank> [world]");
                    return true;
                }
                String rank = args[3];
                String world = args.length > 4 ? args[4] : (onlinePlayer != null ? onlinePlayer.getWorld().getName() : "world");
                if (multiWorldManager.setWorldRank(playerUUID, world, rank)) {
                    sender.sendMessage(ChatColor.GREEN + "Set world rank for " + playerName + " in " + world);
                } else {
                    sender.sendMessage(ChatColor.RED + "Failed to set world rank!");
                }
                return true;
            case "remove":
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rank world remove <player> [world]");
                    return true;
                }
                world = args.length > 3 ? args[3] : (onlinePlayer != null ? onlinePlayer.getWorld().getName() : "world");
                if (multiWorldManager.removeWorldRank(playerUUID, world)) {
                    sender.sendMessage(ChatColor.GREEN + "Removed world rank for " + playerName + " in " + world);
                } else {
                    sender.sendMessage(ChatColor.RED + "No world rank found!");
                }
                return true;
            case "get":
                world = args.length > 3 ? args[3] : (onlinePlayer != null ? onlinePlayer.getWorld().getName() : "world");
                String worldRank = multiWorldManager.getWorldRank(playerUUID, world);
                if (worldRank != null) {
                    sender.sendMessage(ChatColor.GREEN + playerName + "'s rank in " + world + ": " + worldRank);
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "No world-specific rank. Using global rank.");
                }
                return true;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown action!");
                return true;
        }
    }
    
    @SuppressWarnings("unused")
    private boolean handleMetricsCommand(CommandSender sender, String[] args) {
        Map<String, Object> metrics = metricsManager.getAllMetrics();
        sender.sendMessage(ChatColor.GOLD + "=== Plugin Metrics ===");
        sender.sendMessage(ChatColor.YELLOW + "Uptime: " + metrics.get("uptime_seconds") + " seconds");
        sender.sendMessage(ChatColor.YELLOW + "Counters:");
        @SuppressWarnings("unchecked")
        Map<String, Long> counters = (Map<String, Long>) metrics.get("counters");
        for (Map.Entry<String, Long> entry : counters.entrySet()) {
            sender.sendMessage(ChatColor.GRAY + "  " + entry.getKey() + ": " + entry.getValue());
        }
        return true;
    }
    
    public com.excrele.managers.MetricsManager getMetricsManager() {
        return metricsManager;
    }
    
    public com.excrele.managers.MultiWorldManager getMultiWorldManager() {
        return multiWorldManager;
    }
    
    public com.excrele.managers.MigrationManager getMigrationManager() {
        return migrationManager;
    }
    
    public com.excrele.managers.ConfirmationManager getConfirmationManager() {
        return confirmationManager;
    }
    
    public com.excrele.managers.ProgressManager getProgressManager() {
        return progressManager;
    }
    
    public com.excrele.managers.PermissionCacheManager getPermissionCacheManager() {
        return permissionCacheManager;
    }
    
    private boolean handleConfigCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank config <reload|info>");
            return true;
        }
        
        switch (args[1].toLowerCase()) {
            case "reload":
                reloadConfigFile(sender);
                return true;
            case "info":
                sender.sendMessage(ChatColor.GOLD + "=== Configuration Info ===");
                sender.sendMessage(ChatColor.YELLOW + "Config file: config.yml");
                sender.sendMessage(ChatColor.YELLOW + "Auto-reload: " + (configWatcher != null && configWatcher.isRunning() ? "Enabled" : "Disabled"));
                return true;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown config action!");
                return true;
        }
    }
    
    private boolean handleSetPriceCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rank setprice <rank> <price>");
            sender.sendMessage(ChatColor.YELLOW + "Use 0 or 'remove' to remove the price");
            return true;
        }
        
        String rankName = args[1];
        String priceStr = args[2];
        
        if (!rankExists(rankName)) {
            sender.sendMessage(ChatColor.RED + "Rank '" + rankName + "' does not exist!");
            return true;
        }
        
        FileConfiguration ranksConfig = yamlFileManager.getConfig("ranks.yml");
        
        if (priceStr.equalsIgnoreCase("remove") || priceStr.equals("0")) {
            ranksConfig.set("ranks." + rankName + ".price", null);
            yamlFileManager.saveConfigSync("ranks.yml", ranksConfig);
            sender.sendMessage(ChatColor.GREEN + "Price removed for rank '" + rankName + "'!");
        } else {
            try {
                double price = Double.parseDouble(priceStr);
                if (price < 0) {
                    sender.sendMessage(ChatColor.RED + "Price cannot be negative!");
                    return true;
                }
                ranksConfig.set("ranks." + rankName + ".price", price);
                yamlFileManager.saveConfigSync("ranks.yml", ranksConfig);
                sender.sendMessage(ChatColor.GREEN + "Price set to " + vaultIntegration.format(price) + 
                    " for rank '" + rankName + "'!");
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid price! Must be a number.");
            }
        }
        
        return true;
    }
}