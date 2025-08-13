package com.discordbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared utilities class for Discord bot command handlers.
 * Contains common helper methods that can be used across all command sets.
 */
public class BotUtilities {
    private static final Logger logger = LoggerFactory.getLogger(BotUtilities.class);

    /**
     * Gets the username/display name for a user by their ID.
     * This method attempts to get the user's effective name (nickname or display name)
     * from the guild context, falling back to their username, and finally to a fallback
     * message with the user ID if the user cannot be found.
     *
     * @param userId The Discord user ID to look up
     * @param guild The guild context to get member information from (can be null)
     * @param jda The JDA instance to retrieve user information
     * @return The user's display name, username, or a fallback string with the user ID
     */
    public static String getUsernameByID(String userId, Guild guild, JDA jda) {
        if (userId == null || userId.trim().isEmpty()) {
            return "Unknown User";
        }

        // First try to get member info from guild context if available
        if (guild != null) {
            try {
                Member member = guild.getMemberById(userId);
                if (member != null) {
                    String displayName = member.getEffectiveName();
                    return displayName != null ? displayName : member.getUser().getName();
                }
            } catch (Exception e) {
                logger.debug("Failed to get member info for user {} in guild {}: {}", 
                           userId, guild.getId(), e.getMessage());
            }
        }

        // Fallback to JDA user lookup if guild context failed or unavailable
        if (jda != null) {
            try {
                User user = jda.getUserById(userId);
                if (user != null) {
                    return user.getName();
                }
            } catch (Exception e) {
                logger.debug("Failed to get user info for user {}: {}", userId, e.getMessage());
            }
        }

        // Final fallback
        return "Unknown User (" + userId + ")";
    }

    /**
     * Overloaded method for getUsernameByID that only requires guild context.
     * This is useful when you don't have direct access to the JDA instance.
     *
     * @param userId The Discord user ID to look up
     * @param guild The guild context to get member information from
     * @return The user's display name or a fallback string with the user ID
     */
    public static String getUsernameByID(String userId, Guild guild) {
        return getUsernameByID(userId, guild, null);
    }

    /**
     * Overloaded method for getUsernameByID that only requires JDA context.
     * This is useful when you don't have guild context but have JDA access.
     *
     * @param userId The Discord user ID to look up
     * @param jda The JDA instance to retrieve user information
     * @return The user's username or a fallback string with the user ID
     */
    public static String getUsernameByID(String userId, JDA jda) {
        return getUsernameByID(userId, null, jda);
    }
}
