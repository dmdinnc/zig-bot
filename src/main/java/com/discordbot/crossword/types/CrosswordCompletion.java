package com.discordbot.crossword.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a single Mini Crossword completion record for a user.
 * Date is now stored in the storage tree structure, not in individual completions.
 */
public class CrosswordCompletion {
    private final String userId;
    private final int timeInSeconds;
    private final String originalUrl;

    @JsonCreator
    public CrosswordCompletion(
            @JsonProperty("userId") String userId,
            @JsonProperty("timeInSeconds") int timeInSeconds,
            @JsonProperty("originalUrl") String originalUrl) {
        this.userId = userId;
        this.timeInSeconds = timeInSeconds;
        this.originalUrl = originalUrl;
    }

    public String getUserId() {
        return userId;
    }

    public int getTimeInSeconds() {
        return timeInSeconds;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    /**
     * Format time as MM:SS for display
     */
    @JsonIgnore
    public String getFormattedTime() {
        int minutes = timeInSeconds / 60;
        int seconds = timeInSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    @Override
    public String toString() {
        return String.format("CrosswordCompletion{userId=%s, time=%s, url='%s'}", 
                           userId, getFormattedTime(), originalUrl);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        CrosswordCompletion that = (CrosswordCompletion) obj;
        return userId.equals(that.userId) && timeInSeconds == that.timeInSeconds && 
               originalUrl.equals(that.originalUrl);
    }

    @Override
    public int hashCode() {
        return userId.hashCode() + timeInSeconds + originalUrl.hashCode();
    }
}
