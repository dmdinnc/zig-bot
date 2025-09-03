package com.zigbot.features.crossword.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.zigbot.features.crossword.types.CrosswordChannelInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages crossword tracking channel configuration per Discord server.
 * Stores server ID -> channel ID mappings in a JSON file.
 */
public class CrosswordChannelPersistence {
    private static final Logger logger = LoggerFactory.getLogger(CrosswordChannelPersistence.class);
    private static final String DEFAULT_CONFIG_FILE = "crossword-channels.json";
    
    private final String configFilePath;
    private final ObjectMapper objectMapper;
    private final Map<String, List<CrosswordChannelInfo>> serverChannelMap; // serverId -> CrosswordChannelInfo
    
    /**
     * Creates a new CrosswordChannelConfig with default file path.
     */
    public CrosswordChannelPersistence() {
        this(DEFAULT_CONFIG_FILE);
    }
    
    /**
     * Creates a new CrosswordChannelConfig with custom file path (for testing).
     * 
     * @param configFilePath Path to the configuration file
     */
    public CrosswordChannelPersistence(String configFilePath) {
        this.configFilePath = configFilePath;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        // Configure to serialize dates as ISO strings (YYYY-MM-DD) instead of arrays
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.serverChannelMap = new ConcurrentHashMap<>();
        
        loadConfiguration();
        logger.debug("CrosswordChannelConfig initialized with file: {}", configFilePath);
    }
    
    /**
     * Sets the tracking channel for a specific server.
     * 
     * @param serverId The Discord server ID
     * @param channelId The Discord channel ID to track crosswords in
     */
    public void addTrackingChannel(String serverId, String channelId) {
        if (serverId == null || channelId == null) {
            logger.warn("Cannot set tracking channel with null serverId or channelId");
            return;
        }

        List<CrosswordChannelInfo> channelList = serverChannelMap.get(serverId);
        if (channelList == null) {
            channelList = new ArrayList<>();
            serverChannelMap.put(serverId, channelList);
        }
        channelList.add(new CrosswordChannelInfo(channelId));
        
        saveDataToStorage();
        logger.debug("Set tracking channel for server {} to channel {}", serverId, channelId);
    }

    public List<String> getTrackingChannels(String serverId) {
        List<String> channelIds = new ArrayList<>();
        List<CrosswordChannelInfo> infos = serverChannelMap.get(serverId);
        if (infos == null) {
            return channelIds;
        }
        for (CrosswordChannelInfo info : infos) {
            channelIds.add(info.getChannelId());
        }
        return channelIds;
    }
    
    /**
     * Checks if a channel is configured for crossword tracking in the given server.
     * 
     * @param serverId The Discord server ID
     * @param channelId The Discord channel ID to check
     * @return true if this channel is configured for crossword tracking in this server
     */
    public boolean isTrackingChannel(String serverId, String channelId) {
        if (serverId == null || channelId == null) {
            return false;
        }
        List<String> configuredChannelIdList = getTrackingChannels(serverId);
        return configuredChannelIdList != null && configuredChannelIdList.contains(channelId);
    }
    
    /**
     * Removes the tracking channel configuration for a server.
     * 
     * @param serverId The Discord server ID
     */
    public void removeTrackingChannel(String serverId, String channelId) {
        List<CrosswordChannelInfo> channelList = serverChannelMap.get(serverId);
        if (channelList != null) {
            channelList.removeIf(channel -> channel.getChannelId().equals(channelId));
        }
        if (channelList == null || channelList.isEmpty()) {
            serverChannelMap.remove(serverId);
        }
        saveDataToStorage();
        logger.debug("Removed tracking channel for server {} to channel {}", serverId, channelId);
    }
    
    /**
     * Returns the channel info object for a server+channel pair, or null if not found.
     */
    public CrosswordChannelInfo getChannelInfo(String serverId, String channelId) {
        List<CrosswordChannelInfo> infos = serverChannelMap.get(serverId);
        if (infos == null) return null;
        return infos.stream()
                .filter(c -> c.getChannelId().equals(channelId))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Updates streak counters based on whether the scheduled daily post had results.
     * This must only be called for real scheduled posts (not manual test calls).
     */
    public void updateStreakOnScheduledPost(String serverId, String channelId, boolean hadResults) {
        CrosswordChannelInfo info = getChannelInfo(serverId, channelId);
        if (info == null) {
            logger.warn("Cannot update streak for unconfigured server/channel: {}/{}", serverId, channelId);
            return;
        }
        if (hadResults) {
            info.incrementStreak();
        } else {
            info.resetStreak();
        }
        saveDataToStorage();
        logger.debug("Updated streak for server {} channel {} -> current={}, best={}", serverId, channelId, info.getCurrentStreak(), info.getBestStreak());
    }

    public int getCurrentStreak(String serverId, String channelId) {
        CrosswordChannelInfo info = getChannelInfo(serverId, channelId);
        return info != null ? info.getCurrentStreak() : 0;
    }

    public int getBestStreak(String serverId, String channelId) {
        CrosswordChannelInfo info = getChannelInfo(serverId, channelId);
        return info != null ? info.getBestStreak() : 0;
    }
    
    /**
     * Gets all server IDs that have configured crossword tracking channels.
     * 
     * @return Set of server IDs
     */
    public Set<String> getAllServerIds() {
        return new HashSet<>(serverChannelMap.keySet());
    }
    
    /**
     * Checks if a leaderboard has already been posted for the given date in the specified server.
     * 
     * @param serverId The Discord server ID
     * @param channelId The Discord channel ID
     * @param date The date to check
     * @return true if a leaderboard has already been posted for this date
     */
    public boolean hasPostedForDate(String serverId, String channelId, LocalDate date) {
        List<CrosswordChannelInfo> info = serverChannelMap.get(serverId);
        if (info == null) {
            return false;
        }
        return info.stream().anyMatch(channel -> channel.getChannelId().equals(channelId) && channel.hasPostedForDate(date));
    }
    
    /**
     * Updates the last posted date for a server's crossword channel.
     * 
     * @param serverId The Discord server ID
     * @param channelId The Discord channel ID
     * @param date The date when the leaderboard was posted
     */
    public void updateLastPostedDate(String serverId, String channelId, LocalDate date) {
        List<CrosswordChannelInfo> info = serverChannelMap.get(serverId);
        if (info != null) {
            info.stream()
                .filter(channel -> channel.getChannelId().equals(channelId))
                .forEach(channel -> channel.setLastPostedDate(date));
            saveDataToStorage();
            logger.debug("Updated last posted date for server {} to {}", serverId, date);
        } else {
            logger.warn("Cannot update last posted date for unconfigured server: {}", serverId);
        }
    }
    
    /**
     * Gets all configured server-channel mappings.
     * 
     * @return A copy of the server-channel configuration map
     */
    public Map<String, List<String>> getAllConfigurations() {
        Map<String, List<String>> result = new ConcurrentHashMap<>();
        for (Map.Entry<String, List<CrosswordChannelInfo>> entry : serverChannelMap.entrySet()) {
            for (CrosswordChannelInfo info : entry.getValue()) {
                result.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(info.getChannelId());
            }
        }
        return result;
    }
    
    /**
     * Loads configuration from the JSON file.
     * Handles both old format (serverId -> channelId) and new format (serverId -> CrosswordChannelInfo).
     */
    private void loadConfiguration() {
        File configFile = new File(configFilePath);
        if (!configFile.exists()) {
            logger.debug("Configuration file {} does not exist, starting with empty configuration", configFilePath);
            return;
        }

        try {
            TypeReference<Map<String, List<CrosswordChannelInfo>>> newTypeRef = new TypeReference<Map<String, List<CrosswordChannelInfo>>>() {};
            Map<String, List<CrosswordChannelInfo>> loadedConfig = objectMapper.readValue(configFile, newTypeRef);
            serverChannelMap.putAll(loadedConfig);
            logger.debug("Loaded crossword channel configuration for {} servers", serverChannelMap.size());
        } catch (IOException e) {
            logger.error("Failed to load crossword channel configuration from {}: {}", configFilePath, e.getMessage());
            logger.debug("Starting with empty configuration due to load failure");
        }
    }
    
    /**
     * Saves current configuration to the JSON file.
     */
    public void saveDataToStorage() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(configFilePath), serverChannelMap);
            logger.debug("Saved crossword channel configuration to {}", configFilePath);
        } catch (IOException e) {
            logger.error("Failed to save crossword channel configuration to {}: {}", configFilePath, e.getMessage());
        }
    }
}
