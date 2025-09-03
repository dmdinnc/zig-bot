package com.zigbot.features.feedback;

import com.zigbot.BotCommandHandler;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class FeedbackCommandHandler implements BotCommandHandler {
    private static final Logger logger = LoggerFactory.getLogger(FeedbackCommandHandler.class);
    
    private final String feedbackChannelId;
    private final String feedbackServerId;
    private JDA jda;
    private volatile boolean shuttingDown = false;

    public FeedbackCommandHandler(Properties config) {
        this.feedbackChannelId = config.getProperty("feedback.channel.id");
        this.feedbackServerId = config.getProperty("feedback.server.id");
        
        if (feedbackChannelId == null || feedbackChannelId.trim().isEmpty()) {
            logger.warn("Feedback channel ID not configured in .env-variables");
        }
        if (feedbackServerId == null || feedbackServerId.trim().isEmpty()) {
            logger.warn("Feedback server ID not configured in .env-variables");
        }
    }

    @Override
    public List<CommandData> getCommandData() {
        List<CommandData> commands = new ArrayList<>();
        
        commands.add(Commands.slash("feedback", "Submit feedback about the bot")
            .addOption(OptionType.STRING, "message", "Your feedback message", true)
            .addOption(OptionType.STRING, "category", "Category: feature set, modpack name, or be creative!", false));
            
        commands.add(Commands.slash("featurerequest", "Request a new feature for the bot")
            .addOption(OptionType.STRING, "message", "Describe the feature you'd like to see", true)
            .addOption(OptionType.STRING, "category", "Category: feature set, modpack name, or be creative!", false));
        
        return commands;
    }

    @Override
    public boolean handleCommand(SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        
        if (!commandName.equals("feedback") && !commandName.equals("featurerequest")) {
            return false;
        }

        // Guard against shutdown state to avoid scheduling/sending during shutdown
        try {
            if (shuttingDown || jda == null || jda.getStatus() == JDA.Status.SHUTTING_DOWN || jda.getStatus() == JDA.Status.SHUTDOWN) {
                event.reply("⚠️ Bot is shutting down. Please try again after it restarts.").setEphemeral(true).queue();
                return true;
            }
        } catch (Exception e) {
            logger.debug("Feedback handler received command during shutdown; ignoring.");
            return true;
        }

        String message = event.getOption("message").getAsString();
        String category = event.getOption("category") != null ? event.getOption("category").getAsString() : null;
        
        // Validate message is not empty
        if (message == null || message.trim().isEmpty()) {
            event.reply("❌ Message cannot be empty! Please provide your feedback or feature request.")
                .setEphemeral(true).queue();
            return true;
        }

        // Check if feedback channel is configured
        if (feedbackChannelId == null || feedbackServerId == null || 
            feedbackChannelId.trim().isEmpty() || feedbackServerId.trim().isEmpty()) {
            event.reply("❌ Feedback system is not configured. Please contact an administrator.")
                .setEphemeral(true).queue();
            logger.error("Feedback system not configured - missing channel or server ID");
            return true;
        }

        // Process the feedback/feature request
        if (commandName.equals("feedback")) {
            handleFeedback(event, message, category);
        } else {
            handleFeatureRequest(event, message, category);
        }
        
        return true;
    }

    private void handleFeedback(SlashCommandInteractionEvent event, String message, String category) {
        User user = event.getUser();
        
        String feedbackMessage = createFeedbackMessage(user, message, "FEEDBACK", category);
        
        sendToFeedbackChannel(feedbackMessage, 
            () -> {
                event.reply("✅ Thank you for your feedback! It has been forwarded to the development team.")
                    .setEphemeral(true).queue();
                logger.info("Feedback submitted by user {} ({}): {} [Category: {}]", user.getName(), user.getId(), message, category != null ? category : "None");
            },
            error -> {
                event.reply("❌ Failed to submit feedback. Please try again later or contact an administrator.")
                    .setEphemeral(true).queue();
                logger.error("Failed to send feedback to channel: {}", error.getMessage(), error);
            });
    }

    private void handleFeatureRequest(SlashCommandInteractionEvent event, String message, String category) {
        User user = event.getUser();
        
        String featureRequestMessage = createFeedbackMessage(user, message, "FEATURE REQUEST", category);
        
        sendToFeedbackChannel(featureRequestMessage,
            () -> {
                event.reply("✅ Thank you for your feature request! It has been forwarded to the development team.")
                    .setEphemeral(true).queue();
                logger.info("Feature request submitted by user {} ({}): {} [Category: {}]", user.getName(), user.getId(), message, category != null ? category : "None");
            },
            error -> {
                event.reply("❌ Failed to submit feature request. Please try again later or contact an administrator.")
                    .setEphemeral(true).queue();
                logger.error("Failed to send feature request to channel: {}", error.getMessage(), error);
            });
    }

    private String createFeedbackMessage(User user, String message, String type, String category) {
        String categoryLine = (category != null && !category.trim().isEmpty()) 
            ? "🏷️ **Category:** " + category + "\n" 
            : "";
            
        return String.format(
            "🔔 **New %s**\n\n" +
            "👤 **User:** %s (`%s`)\n" +
            "%s" +
            "💬 **Message:**\n%s\n\n" +
            "_Submitted at: <t:%d:F>_",
            type,
            user.getName(),
            user.getId(),
            categoryLine,
            message,
            System.currentTimeMillis() / 1000
        );
    }

    private void sendToFeedbackChannel(String message, Runnable onSuccess, java.util.function.Consumer<Throwable> onError) {
        try {
            if (jda == null) {
                onError.accept(new IllegalStateException("JDA not initialized"));
                return;
            }
            if (shuttingDown || jda.getStatus() == JDA.Status.SHUTTING_DOWN || jda.getStatus() == JDA.Status.SHUTDOWN) {
                onError.accept(new IllegalStateException("JDA is shutting down"));
                return;
            }

            TextChannel feedbackChannel = jda.getTextChannelById(feedbackChannelId);
            if (feedbackChannel == null) {
                onError.accept(new IllegalStateException("Feedback channel not found: " + feedbackChannelId));
                return;
            }

            feedbackChannel.sendMessage(message).queue(
                success -> onSuccess.run(),
                onError
            );
        } catch (Exception e) {
            onError.accept(e);
        }
    }

    @Override
    public void initialize(JDA jda) {
        this.jda = jda;
        logger.info("FeedbackCommandHandler initialized with channel: {} in server: {}", 
            feedbackChannelId, feedbackServerId);
    }

    @Override
    public void shutdown() {
        shuttingDown = true;
        logger.info("FeedbackCommandHandler shutdown complete");
    }

    @Override
    public String getHandlerName() {
        return "FeedbackCommandHandler";
    }
}
