package com.discordbot.crossword.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * Represents tracking information for a crossword channel.
 * Contains the channel ID and the last date when a leaderboard was posted.
 */
public class CrosswordChannelInfo {
    private final String channelId;
    private LocalDate lastPostedDate;
    
    @JsonCreator
    public CrosswordChannelInfo(
            @JsonProperty("channelId") String channelId,
            @JsonProperty("lastPostedDate") LocalDate lastPostedDate) {
        this.channelId = channelId;
        this.lastPostedDate = lastPostedDate;
    }
    
    /**
     * Creates a new CrosswordChannelInfo with no last posted date.
     */
    public CrosswordChannelInfo(String channelId) {
        this(channelId, null);
    }
    
    public String getChannelId() {
        return channelId;
    }
    
    public LocalDate getLastPostedDate() {
        return lastPostedDate;
    }
    
    public void setLastPostedDate(LocalDate lastPostedDate) {
        this.lastPostedDate = lastPostedDate;
    }
    
    /**
     * Checks if a leaderboard has already been posted for the given date.
     */
    public boolean hasPostedForDate(LocalDate date) {
        return lastPostedDate != null && lastPostedDate.equals(date);
    }
    
    @Override
    public String toString() {
        return String.format("CrosswordChannelInfo{channelId='%s', lastPostedDate=%s}", 
                           channelId, lastPostedDate);
    }
}
