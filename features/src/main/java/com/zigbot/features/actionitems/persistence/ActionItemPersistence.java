package com.zigbot.features.actionitems.persistence;

import com.zigbot.features.actionitems.types.ActionItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ActionItemPersistence {
    private static final Logger logger = LoggerFactory.getLogger(ActionItemPersistence.class);
    private static final String DATA_FILE = "action-items.json";
    private final ObjectMapper objectMapper;
    
    // In-memory data storage - restructured by server and channel
    // Structure: serverID -> channelID -> List<ActionItem>
    private final Map<String, Map<String, List<ActionItem>>> serverChannelActionItems = new ConcurrentHashMap<>();
    private int globalItemCounter = 0;

    public ActionItemPersistence() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        // Load existing data on initialization
        loadAndInitializeData();
    }

    public Map<String, Map<String, List<ActionItem>>> loadActionItems() {
        File dataFile = new File(DATA_FILE);
        if (!dataFile.exists()) {
            logger.debug("Data file {} does not exist, starting with empty action items", DATA_FILE);
            return new ConcurrentHashMap<>();
        }

        try {
            // Try to load new format first (server -> channel -> items)
            TypeReference<Map<String, Map<String, List<ActionItem>>>> newTypeRef = 
                new TypeReference<Map<String, Map<String, List<ActionItem>>>>() {};
            
            Map<String, Map<String, List<ActionItem>>> items = objectMapper.readValue(dataFile, newTypeRef);
            int totalItems = items.values().stream()
                    .mapToInt(serverData -> serverData.values().stream()
                        .mapToInt(List::size).sum())
                    .sum();
            logger.debug("Loaded {} action items from {} servers in new format", totalItems, items.size());
            return new ConcurrentHashMap<>(items);
        } catch (IOException e) {
            logger.error("Error loading action items from {}: {}", DATA_FILE, e.getMessage(), e);
            return new ConcurrentHashMap<>();
        }
    }

    public Map<String, Map<String, ActionItem>> loadUserActionItems() {
        Map<String, Map<String, List<ActionItem>>> allItems = loadActionItems();
        Map<String, Map<String, ActionItem>> userItems = new ConcurrentHashMap<>();
        
        for (Map.Entry<String, Map<String, List<ActionItem>>> serverEntry : allItems.entrySet()) {
            for (Map.Entry<String, List<ActionItem>> channelEntry : serverEntry.getValue().entrySet()) {
                for (ActionItem item : channelEntry.getValue()) {
                    String userId = item.getOwnerUserId();
                    userItems.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                            .put(channelEntry.getKey(), item);
                }
            }
        }
        
        return userItems;
    }

    public void saveActionItems(Map<String, Map<String, List<ActionItem>>> serverChannelActionItems) {
        try {
            objectMapper.writeValue(new File(DATA_FILE), serverChannelActionItems);
            int totalItems = serverChannelActionItems.values().stream()
                .mapToInt(serverData -> serverData.values().stream()
                    .mapToInt(List::size).sum())
                .sum();
            logger.debug("Saved {} action items across {} servers to {}", totalItems, serverChannelActionItems.size(), DATA_FILE);
        } catch (IOException e) {
            logger.error("Error saving action items to {}: {}", DATA_FILE, e.getMessage(), e);
        }
    }

    public CompletableFuture<Void> saveActionItemsAsync(Map<String, Map<String, List<ActionItem>>> serverChannelActionItems) {
        return CompletableFuture.runAsync(() -> saveActionItems(serverChannelActionItems))
            .exceptionally(throwable -> {
                logger.error("Async save failed", throwable);
                return null;
            });
    }
    
    /**
     * Load existing data from file and initialize in-memory structures
     */
    private void loadAndInitializeData() {
        Map<String, Map<String, List<ActionItem>>> loadedItems = loadActionItems();
        serverChannelActionItems.clear();
        serverChannelActionItems.putAll(loadedItems);
    }
    
    // === DATA MANAGEMENT METHODS ===
    
    /**
     * Add a new action item
     */
    public ActionItem addActionItem(String serverId, String ownerUserId, String channelId, String description, ActionItem.Cadence cadence) {
        int nextGlobalId = ++globalItemCounter;
        ActionItem item = new ActionItem(ownerUserId, description, cadence, nextGlobalId);
        
        // Add to both maps
        serverChannelActionItems.computeIfAbsent(serverId, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(channelId, k -> new ArrayList<>())
            .add(item);
        
        // Save to file asynchronously
        saveActionItemsAsync(serverChannelActionItems);
        
        logger.debug("Added action item {} to channel {}", item.getId(), channelId);
        return item;
    }
    
    /**
     * Remove an action item by ID
     */
    public boolean removeActionItem(String serverId, String channelId, String itemId) {
        // Remove from server/channel structure - we need to find which channel contains this item
        Map<String, List<ActionItem>> serverData = serverChannelActionItems.get(serverId);
        String foundChannelId = null;
        if (serverData != null) {
            for (Map.Entry<String, List<ActionItem>> channelEntry : serverData.entrySet()) {
                if (channelEntry.getValue().removeIf(ai -> ai.getId().equals(itemId))) {
                    foundChannelId = channelEntry.getKey();
                    break;
                }
            }
        }
        if (foundChannelId != null && serverData != null) {
            List<ActionItem> channelItems = serverData.get(foundChannelId);
            if (channelItems != null && channelItems.isEmpty()) {
                serverData.remove(foundChannelId);
            }
            if (serverData.isEmpty()) {
                serverChannelActionItems.remove(serverId);
            }
        }
        
        // Save to file asynchronously
        saveActionItemsAsync(serverChannelActionItems);
        
        logger.debug("Removed action item {} from channel {}", itemId, foundChannelId);
        return true;
    }
    
    /**
     * Assign an action item to a different user
     */
    public boolean assignActionItem(String serverId, String channelId, String itemId, String newOwnerUserId) {
        ActionItem item = getActionItem(serverId, channelId, itemId);
        if (item != null) {
            item.setOwnerUserId(newOwnerUserId);
            
            // Save to file asynchronously
            saveActionItemsAsync(serverChannelActionItems);
            
            logger.debug("Assigned action item {} to user {}", itemId, newOwnerUserId);
            return true;
        }
        return false;
    }

    /**
     * Get action items for a specific channel
     */
    public List<ActionItem> getActionItemsForChannel(String serverId, String channelId) {
        Map<String, List<ActionItem>> serverData = serverChannelActionItems.get(serverId);
        if (serverData == null) {
            return new ArrayList<>();
        }
        
        List<ActionItem> channelItems = serverData.get(channelId);
        return channelItems != null ? new ArrayList<>(channelItems) : new ArrayList<>();
    }

    /**
     * Get all action items for a user across all channels in a server
     */
    public List<ActionItem> getActionItemsForUser(String serverId, String userId) {
        Map<String, List<ActionItem>> serverData = serverChannelActionItems.get(serverId);
        if (serverData == null) {
            return new ArrayList<>();
        }
        
        return serverData.values().stream()
            .flatMap(List::stream)
            .filter(item -> item.getOwnerUserId().equals(userId))
            .collect(Collectors.toList());
    }

    /**
     * Get an action item by ID
     */
    public ActionItem getActionItem(String serverId, String channelId, String itemId) {
        Map<String, List<ActionItem>> serverData = serverChannelActionItems.get(serverId);
        if (serverData != null) {
            for (Map.Entry<String, List<ActionItem>> channelEntry : serverData.entrySet()) {
                if (channelEntry.getKey() == channelId) {
                    for (ActionItem ai : channelEntry.getValue()) {
                        if (ai.getId() == itemId) return ai;
                    }
                    break;
                }
            }
        }
        return null;
    }

    /**
     * Save current state to file
     */
    public void saveCurrentState() {
        saveActionItems(serverChannelActionItems);
    }

    /**
     * Save current state to file asynchronously
     */
    public void saveCurrentStateAsync() {
        saveActionItemsAsync(serverChannelActionItems);
    }

    public List<String> getAllServers() {
        return new ArrayList<>(serverChannelActionItems.keySet());
    }

    public List<String> getAllChannels(String serverId) {
        return serverChannelActionItems.get(serverId).keySet().stream().collect(Collectors.toList());
    }
}
