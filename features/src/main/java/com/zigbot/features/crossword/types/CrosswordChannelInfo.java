package com.zigbot.features.crossword.types;

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
    private int currentStreak;
    private int bestStreak;

    @JsonCreator
    public CrosswordChannelInfo(
            @JsonProperty("channelId") String channelId,
            @JsonProperty("lastPostedDate") LocalDate lastPostedDate,
            @JsonProperty("currentStreak") Integer currentStreak,
            @JsonProperty("bestStreak") Integer bestStreak) {
        this.channelId = channelId;
        this.lastPostedDate = lastPostedDate;
        this.currentStreak = currentStreak != null ? currentStreak : 0;
        this.bestStreak = bestStreak != null ? bestStreak : 0;
    }

    /**
     * Creates a new CrosswordChannelInfo with no last posted date.
     */
    public CrosswordChannelInfo(String channelId) {
        this(channelId, null, 0, 0);
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

    public int getCurrentStreak() {
        return currentStreak;
    }

    public void setCurrentStreak(int currentStreak) {
        this.currentStreak = currentStreak;
    }

    public int getBestStreak() {
        return bestStreak;
    }

    public void setBestStreak(int bestStreak) {
        this.bestStreak = bestStreak;
    }

    public void incrementStreak() {
        this.currentStreak++;
        if (this.currentStreak > this.bestStreak) {
            this.bestStreak = this.currentStreak;
        }
    }

    public void resetStreak() {
        this.currentStreak = 0;
    }

    /**
     * Checks if a leaderboard has already been posted for the given date.
     */
    public boolean hasPostedForDate(LocalDate date) {
        return lastPostedDate != null && lastPostedDate.equals(date);
    }

    @Override
    public String toString() {
        return String.format("CrosswordChannelInfo{channelId='%s', lastPostedDate=%s, currentStreak=%d, bestStreak=%d}", 
                channelId, lastPostedDate, currentStreak, bestStreak);
    }
}
