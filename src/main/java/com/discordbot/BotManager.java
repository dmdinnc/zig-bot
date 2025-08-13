package com.discordbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Main bot manager that handles multiple command handlers
 */
public class BotManager extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(BotManager.class);
    
    private final List<BotCommandHandler> commandHandlers;
    private JDA jda;
    
    public BotManager() {
        this.commandHandlers = new CopyOnWriteArrayList<>();
    }
    
    /**
     * Register a command handler with the bot manager
     * @param handler The command handler to register
     */
    public void registerCommandHandler(BotCommandHandler handler) {
        commandHandlers.add(handler);
        logger.info("Registered command handler: {}", handler.getHandlerName());
        
        // If JDA is already initialized, initialize the handler immediately
        if (jda != null) {
            handler.initialize(jda);
        }
    }
    
    /**
     * Initialize the bot manager with JDA instance
     * @param jda The JDA instance
     */
    public void initialize(JDA jda) {
        this.jda = jda;
        
        // Initialize all registered handlers
        for (BotCommandHandler handler : commandHandlers) {
            handler.initialize(jda);
        }
        
        logger.info("BotManager initialized with {} command handlers", commandHandlers.size());
    }
    
    @Override
    public void onReady(ReadyEvent event) {
        logger.info("Bot is ready! Registering slash commands...");
        
        // Collect all command data from handlers
        List<CommandData> allCommands = new ArrayList<>();
        for (BotCommandHandler handler : commandHandlers) {
            List<CommandData> handlerCommands = handler.getCommandData();
            if (handlerCommands != null) {
                allCommands.addAll(handlerCommands);
                logger.info("Added {} commands from handler: {}", 
                    handlerCommands.size(), handler.getHandlerName());
            }
        }
        
        // Register all commands globally
        if (!allCommands.isEmpty()) {
            event.getJDA().updateCommands().addCommands(allCommands).queue(
                success -> logger.info("Successfully registered {} slash commands", allCommands.size()),
                error -> logger.error("Failed to register slash commands", error)
            );
        }
    }
    
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        logger.debug("Received slash command: {}", commandName);
        
        // Try each handler until one processes the command
        for (BotCommandHandler handler : commandHandlers) {
            try {
                if (handler.handleCommand(event)) {
                    logger.debug("Command '{}' handled by: {}", commandName, handler.getHandlerName());
                    return;
                }
            } catch (Exception e) {
                logger.error("Error in command handler '{}' for command '{}'", 
                    handler.getHandlerName(), commandName, e);
                event.reply("❌ An error occurred while processing your command. Please try again later.")
                    .setEphemeral(true).queue();
                return;
            }
        }
        
        // No handler processed the command
        logger.warn("No handler found for command: {}", commandName);
        event.reply("❌ Unknown command. Use `/help` to see available commands.")
            .setEphemeral(true).queue();
    }
    
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {        
        // Forward message event to all handlers that implement ListenerAdapter
        for (BotCommandHandler handler : commandHandlers) {
            try {
                if (handler instanceof ListenerAdapter) {
                    ((ListenerAdapter) handler).onMessageReceived(event);
                }
            } catch (Exception e) {
                logger.error("Error in message handler '{}' for message from '{}'", 
                    handler.getHandlerName(), event.getAuthor().getName(), e);
            }
        }
    }
    
    /**
     * Shutdown the bot manager and all command handlers
     */
    public void shutdown() {
        logger.info("Shutting down BotManager...");
        
        for (BotCommandHandler handler : commandHandlers) {
            try {
                handler.shutdown();
                logger.info("Shut down handler: {}", handler.getHandlerName());
            } catch (Exception e) {
                logger.error("Error shutting down handler: {}", handler.getHandlerName(), e);
            }
        }
        
        commandHandlers.clear();
        logger.info("BotManager shutdown complete");
    }
    
    /**
     * Get the number of registered command handlers
     * @return Number of handlers
     */
    public int getHandlerCount() {
        return commandHandlers.size();
    }
}
