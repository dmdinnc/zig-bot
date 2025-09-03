package com.zigbot;

import com.zigbot.features.actionitems.ActionItemCommandHandler;
import com.zigbot.features.crossword.CrosswordCommandHandler;
import com.zigbot.features.feedback.FeedbackCommandHandler;
import com.zigbot.features.imageconverter.ImageToGifCommandHandler;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

public class Main {
    // Note: Logger is initialized AFTER we configure logging to ensure proper log level
    private static Logger logger;

    public static void main(String[] args) {
        try {
            // Configure logging FIRST, before any loggers are created
            configureLoggingFromEnv();
            
            // Now we can safely create the logger
            logger = LoggerFactory.getLogger(Main.class);
            
            Properties config = loadConfig();
            
            // Log the configured log level for confirmation
            configureLogging(config);
            
            String token = config.getProperty("bot.token");
            
            if (token == null || token.trim().isEmpty() || token.equals("YOUR_BOT_TOKEN_HERE")) {
                logger.error("Please set your bot token in config.properties");
                return;
            }

            // Create bot manager and register command handlers
            BotManager botManager = new BotManager();
            ActionItemCommandHandler actionItemHandler = new ActionItemCommandHandler();
            CrosswordCommandHandler crosswordHandler = new CrosswordCommandHandler();
            FeedbackCommandHandler feedbackHandler = new FeedbackCommandHandler(config);
            ImageToGifCommandHandler imageToGifHandler = new ImageToGifCommandHandler();
            botManager.registerCommandHandler(actionItemHandler);
            botManager.registerCommandHandler(crosswordHandler);
            botManager.registerCommandHandler(feedbackHandler);
            botManager.registerCommandHandler(imageToGifHandler);
            
            // Build JDA instance
            JDA jda = JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS)
                .addEventListeners(botManager)
                .build();
            
            // Initialize bot manager
            botManager.initialize(jda);
            
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down bot...");
                botManager.shutdown();
                if (jda != null) {
                    jda.shutdown();
                }
            }));

        } catch (IOException e) {
            logger.error("Failed to load configuration", e);
        }
    }

    private static Properties loadConfig() throws IOException {
        Properties config = new Properties();
        
        // Load from .env-variables file
        String envPath = ".env-variables";
        if (Files.exists(Paths.get(envPath))) {
            try {
                Files.lines(Paths.get(envPath))
                    .filter(line -> !line.trim().isEmpty() && !line.trim().startsWith("#"))
                    .forEach(line -> {
                        String[] parts = line.split("=", 2);
                        if (parts.length == 2) {
                            config.setProperty(parts[0].trim().toLowerCase().replace("_", "."), parts[1].trim());
                        }
                    });
            } catch (IOException e) {
                logger.error("Failed to load .env-variables file", e);
            }
        } else {
            logger.warn(".env-variables file not found. Please create it with your BOT_TOKEN.");
        }
        
        return config;
    }
    
    /**
     * Configure logging level from environment variable BEFORE any loggers are created
     */
    private static void configureLoggingFromEnv() {
        try {
            String envPath = ".env-variables";
            if (Files.exists(Paths.get(envPath))) {
                Files.lines(Paths.get(envPath))
                    .filter(line -> !line.trim().isEmpty() && !line.trim().startsWith("#"))
                    .forEach(line -> {
                        String[] parts = line.split("=", 2);
                        if (parts.length == 2) {
                            String key = parts[0].trim();
                            String value = parts[1].trim();
                            if ("LOG_LEVEL".equals(key)) {
                                // Set system property for Logback to use
                                System.setProperty("LOG_LEVEL", value.toUpperCase());
                                // Also set it as a regular system property for later reference
                                System.setProperty("configured.log.level", value.toUpperCase());
                            }
                        }
                    });
            } else {
                // Default to INFO if no config file
                System.setProperty("LOG_LEVEL", "INFO");
            }
        } catch (IOException e) {
            // If we can't read the config, default to INFO
            System.setProperty("LOG_LEVEL", "INFO");
            System.err.println("Failed to load logging configuration from .env-variables: " + e.getMessage());
        }
    }
    
    /**
     * Configure logging level from environment variable (legacy method, kept for compatibility)
     */
    private static void configureLogging(Properties config) {
        String configuredLevel = System.getProperty("configured.log.level");
        if (configuredLevel != null && logger != null) {
            logger.info("Log level set to: {}", configuredLevel);
        }
    }
}
