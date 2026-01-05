package com.excrele.yaml;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages YAML comments and documentation.
 * Note: Bukkit's YAMLConfiguration doesn't preserve comments natively,
 * so we use a custom approach to add comments when saving.
 */
public class YAMLCommentManager {
    private final JavaPlugin plugin;
    private final YAMLFileManager fileManager;
    private final Map<String, Map<String, String>> comments; // fileName -> path -> comment
    
    public YAMLCommentManager(JavaPlugin plugin, YAMLFileManager fileManager) {
        this.plugin = plugin;
        this.fileManager = fileManager;
        this.comments = new HashMap<>();
        loadComments();
    }
    
    /**
     * Add a comment to a rank.
     */
    public boolean addComment(String rankName, String comment) {
        if (rankName == null || rankName.trim().isEmpty()) {
            return false;
        }
        
        String path = "ranks." + rankName.trim();
        if (!comments.containsKey("ranks.yml")) {
            comments.put("ranks.yml", new HashMap<>());
        }
        comments.get("ranks.yml").put(path, comment);
        
        return saveComments();
    }
    
    /**
     * Remove a comment from a rank.
     */
    public boolean removeComment(String rankName) {
        if (rankName == null || rankName.trim().isEmpty()) {
            return false;
        }
        
        String path = "ranks." + rankName.trim();
        if (comments.containsKey("ranks.yml") && comments.get("ranks.yml").containsKey(path)) {
            comments.get("ranks.yml").remove(path);
            return saveComments();
        }
        
        return false;
    }
    
    /**
     * Get comment for a rank.
     */
    public String getComment(String rankName) {
        if (rankName == null || rankName.trim().isEmpty()) {
            return null;
        }
        
        String path = "ranks." + rankName.trim();
        if (comments.containsKey("ranks.yml")) {
            return comments.get("ranks.yml").get(path);
        }
        
        return null;
    }
    
    /**
     * Add a header comment to a file.
     */
    public boolean addHeaderComment(String fileName, String comment) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return false;
        }
        
        if (!comments.containsKey(fileName)) {
            comments.put(fileName, new HashMap<>());
        }
        comments.get(fileName).put("__HEADER__", comment);
        
        return saveComments();
    }
    
    /**
     * Generate helpful comments for a rank configuration.
     */
    public String generateRankComment(String rankName, FileConfiguration config) {
        StringBuilder comment = new StringBuilder();
        comment.append("# Rank: ").append(rankName).append("\n");
        
        String prefix = config.getString("ranks." + rankName + ".info.prefix", "");
        String suffix = config.getString("ranks." + rankName + ".info.suffix", "");
        int priority = config.getInt("ranks." + rankName + ".priority", 0);
        String track = config.getString("ranks." + rankName + ".track", "");
        List<String> permissions = config.getStringList("ranks." + rankName + ".permissions");
        List<String> inheritance = config.getStringList("ranks." + rankName + ".inheritance");
        
        if (!prefix.isEmpty()) {
            comment.append("# Prefix: ").append(prefix).append("\n");
        }
        if (!suffix.isEmpty()) {
            comment.append("# Suffix: ").append(suffix).append("\n");
        }
        if (priority != 0) {
            comment.append("# Priority: ").append(priority).append(" (higher = more important)\n");
        }
        if (!track.isEmpty()) {
            comment.append("# Track: ").append(track).append("\n");
        }
        if (!permissions.isEmpty()) {
            comment.append("# Permissions: ").append(permissions.size()).append(" permission(s)\n");
        }
        if (!inheritance.isEmpty()) {
            comment.append("# Inherits from: ").append(String.join(", ", inheritance)).append("\n");
        }
        
        return comment.toString();
    }
    
    /**
     * Format YAML with comments.
     */
    public String formatYAMLWithComments(String fileName, FileConfiguration config) {
        try {
            // Save config to temp file first, then read it
            File tempFile = new File(fileManager.getDataFolder(), fileName + ".tmp");
            config.save(tempFile);
            String yamlContent = new String(Files.readAllBytes(tempFile.toPath()));
            tempFile.delete(); // Clean up
            
            // Add header comment if exists
            if (comments.containsKey(fileName) && comments.get(fileName).containsKey("__HEADER__")) {
                String header = comments.get(fileName).get("__HEADER__");
                yamlContent = "# " + header.replace("\n", "\n# ") + "\n" + yamlContent;
            }
            
            // Add comments for ranks
            if (fileName.equals("ranks.yml") && config.contains("ranks")) {
                yamlContent = addRankComments(yamlContent, config);
            }
            
            return yamlContent;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to format YAML with comments: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Add comments for ranks in YAML content.
     */
    private String addRankComments(String yamlContent, FileConfiguration config) {
        StringBuilder result = new StringBuilder();
        String[] lines = yamlContent.split("\n");
        String currentRank = null;
        int indentLevel = 0;
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            
            // Detect rank start
            Matcher rankMatcher = Pattern.compile("^\\s*(\\w+):\\s*$").matcher(line);
            if (rankMatcher.matches() && i > 0 && lines[i-1].contains("ranks:")) {
                currentRank = rankMatcher.group(1);
                indentLevel = line.length() - line.trim().length();
                
                // Add comment if exists
                if (comments.containsKey("ranks.yml")) {
                    String comment = comments.get("ranks.yml").get("ranks." + currentRank);
                    if (comment != null && !comment.isEmpty()) {
                        String[] commentLines = comment.split("\n");
                        for (String commentLine : commentLines) {
                            if (!commentLine.trim().isEmpty()) {
                                result.append(String.format("%" + (indentLevel + 2) + "s", "")).append(commentLine).append("\n");
                            }
                        }
                    } else {
                        // Generate auto-comment
                        String autoComment = generateRankComment(currentRank, config);
                        if (autoComment != null && !autoComment.isEmpty()) {
                            String[] commentLines = autoComment.split("\n");
                            for (String commentLine : commentLines) {
                                if (!commentLine.trim().isEmpty()) {
                                    result.append(String.format("%" + (indentLevel + 2) + "s", "")).append(commentLine).append("\n");
                                }
                            }
                        }
                    }
                }
            }
            
            result.append(line).append("\n");
        }
        
        return result.toString();
    }
    
    /**
     * Load comments from file.
     */
    private void loadComments() {
        File commentsFile = new File(fileManager.getDataFolder(), "comments.yml");
        if (!commentsFile.exists()) {
            return;
        }
        
        try {
            FileConfiguration commentsConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(commentsFile);
            for (String fileName : commentsConfig.getKeys(false)) {
                Map<String, String> fileComments = new HashMap<>();
                if (commentsConfig.isConfigurationSection(fileName)) {
                    for (String path : commentsConfig.getConfigurationSection(fileName).getKeys(false)) {
                        fileComments.put(path, commentsConfig.getString(fileName + "." + path));
                    }
                }
                comments.put(fileName, fileComments);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load comments: " + e.getMessage());
        }
    }
    
    /**
     * Save comments to file.
     */
    private boolean saveComments() {
        try {
            File commentsFile = new File(fileManager.getDataFolder(), "comments.yml");
            FileConfiguration commentsConfig = new org.bukkit.configuration.file.YamlConfiguration();
            
            for (Map.Entry<String, Map<String, String>> fileEntry : comments.entrySet()) {
                for (Map.Entry<String, String> commentEntry : fileEntry.getValue().entrySet()) {
                    commentsConfig.set(fileEntry.getKey() + "." + commentEntry.getKey(), commentEntry.getValue());
                }
            }
            
            commentsConfig.save(commentsFile);
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save comments: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get all comments for a file.
     */
    public Map<String, String> getFileComments(String fileName) {
        return comments.getOrDefault(fileName, new HashMap<>());
    }
}

