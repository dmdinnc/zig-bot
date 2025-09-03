package com.zigbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import java.util.List;

/**
 * Interface for bot command handlers that can be managed by the BotManager
 */
public interface BotCommandHandler {
    
    /**
     * Get the command data for registering slash commands
     * @return List of CommandData for this handler
     */
    List<CommandData> getCommandData();
    
    /**
     * Handle a slash command interaction
     * @param event The slash command interaction event
     * @return true if this handler processed the command, false otherwise
     */
    boolean handleCommand(SlashCommandInteractionEvent event);
    
    /**
     * Initialize the command handler with JDA instance
     * @param jda The JDA instance
     */
    void initialize(JDA jda);
    
    /**
     * Shutdown the command handler and clean up resources
     */
    void shutdown();
    
    /**
     * Get the name of this command handler for logging
     * @return Handler name
     */
    String getHandlerName();
}
