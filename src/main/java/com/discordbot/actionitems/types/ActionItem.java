package com.discordbot.actionitems.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ActionItem {
    public static final int MAX_DESCRIPTION_LENGTH = 100;
    private final String id;
    private String ownerUserId;
    private final String description;
    private final Cadence cadence;
    private final LocalDateTime creationTime;
    private LocalDateTime lastNotified;

    public enum Cadence {
        DAILY,
        WEEKLY,
        MONTHLY
    }

    public ActionItem(String ownerUserId, String description, Cadence cadence, int globalItemNumber) {
        if (description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Description cannot be empty. Please provide a meaningful description for your action item.");
        }
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("Description cannot exceed " + MAX_DESCRIPTION_LENGTH + " characters. Current length: " + description.length());
        }
        this.id = String.valueOf(globalItemNumber);
        this.ownerUserId = ownerUserId;
        this.description = encodeDescription(description);
        this.cadence = cadence;
        this.creationTime = LocalDateTime.now();
        this.lastNotified = creationTime;
    }

    // Default constructor for Jackson
    public ActionItem() {
        this.id = "0";
        this.ownerUserId = "";
        this.description = "";
        this.cadence = Cadence.DAILY;
        this.creationTime = LocalDateTime.now();
        this.lastNotified = creationTime;
    }

    @JsonCreator
    public ActionItem(@JsonProperty("id") String id,
                     @JsonProperty("ownerUserId") String ownerUserId,
                     @JsonProperty("description") String description,
                     @JsonProperty("cadence") Cadence cadence,
                     @JsonProperty("creationTime") LocalDateTime creationTime,
                     @JsonProperty("lastNotified") LocalDateTime lastNotified) {
        this.id = id;
        this.ownerUserId = ownerUserId != null ? ownerUserId : "";
        // Description is already base64 encoded when loaded from JSON
        this.description = description != null ? description : "";
        this.cadence = cadence;
        this.creationTime = creationTime;
        this.lastNotified = lastNotified;
    }

    // Getters
    public String getId() { return id; }
    public String getOwnerUserId() { return ownerUserId; }
    @JsonIgnore
    public String getDescription() { return decodeDescription(description); }
    @JsonProperty("description")
    public String getEncodedDescription() { return description; }
    public Cadence getCadence() { return cadence; }
    public LocalDateTime getCreationTime() { return creationTime; }
    public LocalDateTime getLastNotified() { return lastNotified; }

    // Setters
    public void setLastNotified(LocalDateTime lastNotified) {
        this.lastNotified = lastNotified;
    }
    
    public void setOwnerUserId(String ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    // Base64 encoding/decoding utility methods
    private String encodeDescription(String description) {
        if (description == null || description.isEmpty()) {
            return description;
        }
        return Base64.getEncoder().encodeToString(description.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeDescription(String encodedDescription) {
        if (encodedDescription == null || encodedDescription.isEmpty()) {
            return encodedDescription;
        }
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(encodedDescription);
            return new String(decodedBytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // If decoding fails, assume it's already plain text (for backward compatibility)
            return encodedDescription;
        }
    }

    @Override
    public String toString() {
        return String.format("ActionItem{id='%s', ownerUserId='%s', description='%s', cadence=%s, creationTime=%s, lastNotified=%s}",
                id, ownerUserId, getDescription(), cadence, creationTime, lastNotified);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ActionItem that = (ActionItem) o;
        return id.equals(that.id) && ownerUserId.equals(that.ownerUserId);
    }

    @Override
    public int hashCode() {
        return id.hashCode() + ownerUserId.hashCode();
    }
}
