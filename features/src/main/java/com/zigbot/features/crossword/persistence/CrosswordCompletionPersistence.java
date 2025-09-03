package com.zigbot.features.crossword.persistence;

import com.zigbot.features.crossword.types.CrosswordCompletion;
import com.zigbot.features.crossword.types.FullCompletionRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Storage format
 * serverId:
 *   channelId:
 *     date:
 *       [completion, completion, ...]
 */

public class CrosswordCompletionPersistence {
    private static final Logger logger = LoggerFactory.getLogger(CrosswordCompletionPersistence.class);
    private static final String CROSSWORD_DATA_FILE = "crossword-completions-v2.json";
    
    private final ObjectMapper objectMapper;
    private final File dataFile;
    
    // In-memory data storage: serverId -> channelId -> date -> [completions]
    private final Map<String, Map<String, Map<LocalDate, List<CrosswordCompletion>>>> completions = new ConcurrentHashMap<>();

    public CrosswordCompletionPersistence() {
        this(CROSSWORD_DATA_FILE);
    }

    public CrosswordCompletionPersistence(String dataFilePath) {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        // Configure to serialize dates as ISO strings (YYYY-MM-DD) instead of arrays
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.dataFile = new File(dataFilePath);
        
        loadDataFromStorage();
        logger.debug("CrosswordPersistence initialized with file: {}", dataFile.getAbsolutePath());
    }

    private void loadDataFromStorage() {
        try {
            if (dataFile.exists()) {
                completions.clear();
                completions.putAll(
                    objectMapper.readValue(
                        dataFile, new TypeReference<Map<String, Map<String, Map<LocalDate, List<CrosswordCompletion>>>>>() {}));
            }
        } catch (IOException e) {
            logger.error("Failed to load crossword completions from file: {}", dataFile.getAbsolutePath(), e);
        }
    }

    public void saveDataToStorage() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(dataFile, completions);
        } catch (IOException e) {
            logger.error("Failed to save crossword completions to file: {}", dataFile.getAbsolutePath(), e);
        }
    }

    public void addCompletion(String serverId, String channelId, LocalDate date, CrosswordCompletion completion) {
        completions.computeIfAbsent(serverId, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(channelId, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(date, k -> new ArrayList<>())
            .add(completion);
        saveDataToStorage();
    }

    public List<CrosswordCompletion> getCompletions(String serverId, String channelId, LocalDate date) {
        return completions.getOrDefault(serverId, new ConcurrentHashMap<>())
            .getOrDefault(channelId, new ConcurrentHashMap<>())
            .getOrDefault(date, new ArrayList<>());
    }

    public void removeCompletion(String serverId, String channelId, LocalDate date, CrosswordCompletion completion) {
        completions.getOrDefault(serverId, new ConcurrentHashMap<>())
            .getOrDefault(channelId, new ConcurrentHashMap<>())
            .getOrDefault(date, new ArrayList<>())
            .remove(completion);
        saveDataToStorage();
    }

    public List<FullCompletionRecord> getCompletionsForUser(String serverId, String channelId, String userId) {
        List<FullCompletionRecord> completionsForUser = new ArrayList<>();
        completions.getOrDefault(serverId, new ConcurrentHashMap<>())
            .getOrDefault(channelId, new ConcurrentHashMap<>())
            .forEach((date, completions) -> {
                completions.stream()
                    .filter(completion -> completion.getUserId().equals(userId))
                    .forEach(completion -> completionsForUser.add(new FullCompletionRecord(date, completion)));
            });
        completionsForUser.sort((c1, c2) -> c2.getDate().compareTo(c1.getDate()));
        return completionsForUser;
    }

    public FullCompletionRecord getBestTime(String serverId, String channelId, String userId) {
        List<FullCompletionRecord> userCompletions = getCompletionsForUser(serverId, channelId, userId);
        if (userCompletions.isEmpty()) {
            return null;
        }
        return userCompletions.stream()
            .min(Comparator.comparingInt(c -> c.getCompletion().getTimeInSeconds()))
            .orElse(null);
    }
}
