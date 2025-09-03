package com.zigbot.features.actionitems;

import com.zigbot.BotCommandHandler;
import com.zigbot.BotUtilities;
import com.zigbot.features.actionitems.persistence.ActionItemPersistence;
import com.zigbot.features.actionitems.types.ActionItem;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ActionItemCommandHandler implements BotCommandHandler {
    private static final Logger logger = LoggerFactory.getLogger(ActionItemCommandHandler.class);
    private final ActionItemPersistence persistence;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> dailyCheckFuture;
    private ScheduledFuture<?> initialCheckFuture;
    private volatile boolean shuttingDown = false;
    private JDA jda;
    private boolean developmentCommandsEnabled = false;

    public ActionItemCommandHandler() {
        persistence = new ActionItemPersistence();
        
        logger.debug("Initialized ActionItemCommandHandler with persistence layer");
        
        // Load development commands configuration
        loadDevelopmentConfig();
    }

    @Override
    public List<CommandData> getCommandData() {
        List<CommandData> commands = new ArrayList<>();
        
        // Create base subcommands
        List<SubcommandData> subcommands = new ArrayList<>();
        subcommands.add(new SubcommandData("add", "Add a new action item")
            .addOption(OptionType.STRING, "description", "Description of the action item", true)
            .addOptions(new OptionData(OptionType.STRING, "cadence", "How often to be reminded", true)
                .addChoice("Daily", "DAILY")
                .addChoice("Weekly", "WEEKLY")
                .addChoice("Monthly", "MONTHLY")));
        subcommands.add(new SubcommandData("list", "List all your action items"));
        subcommands.add(new SubcommandData("remove", "Remove an action item")
            .addOption(OptionType.STRING, "id", "ID of the action item to remove", true));
        subcommands.add(new SubcommandData("assign", "Assign an action item to a user")
            .addOption(OptionType.STRING, "id", "ID of the action item to assign", true)
            .addOption(OptionType.USER, "user", "User to assign the action item to", true));
        subcommands.add(new SubcommandData("help", "Show help information"));
        
        // Add test command only if development commands are enabled
        if (developmentCommandsEnabled) {
            subcommands.add(new SubcommandData("test", "Force daily notification check (development only)"));
        }
        
        // Create the main /actionitem command with subcommands
        CommandData actionItemCommand = Commands.slash("actionitem", "Manage your action items")
            .addSubcommands(subcommands);
        // And the short version (reuse the same subcommands list)
        CommandData aiCommand = Commands.slash("ai", "Manage your action items")
            .addSubcommands(subcommands);
        
        commands.add(actionItemCommand);
        commands.add(aiCommand);
        return commands;
    }

    @Override
    public boolean handleCommand(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("actionitem") && !event.getName().equals("ai")) {
            return false;
        }

        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            event.reply("❌ Please specify a subcommand. Use `/actionitem help` for more information.")
                .setEphemeral(true).queue();
            return true;
        }

        switch (subcommand) {
            case "add":
                handleAddCommand(event);
                break;
            case "list":
                handleListCommand(event);
                break;
            case "remove":
                handleRemoveCommand(event);
                break;
            case "assign":
                handleAssignCommand(event);
                break;
            case "help":
                handleHelpCommand(event);
                break;
            case "test":
                if (developmentCommandsEnabled) {
                    handleTestCommand(event);
                } else {
                    event.reply("❌ Development commands are not enabled.").setEphemeral(true).queue();
                }
                break;
            default:
                event.reply("❌ Unknown subcommand. Use `/actionitem help` for available commands.")
                    .setEphemeral(true).queue();
        }
        
        return true;
    }

    @Override
    public void initialize(JDA jda) {
        this.jda = jda;
        scheduleDailyCheck();
        
        // Schedule initial notification check to run after JDA is fully ready
        try {
            if (!shuttingDown && !scheduler.isShutdown()) {
                initialCheckFuture = scheduler.schedule(() -> {
                    logger.debug("Running initial notification check after JDA initialization");
                    runDailyNotificationCheck(false);
                }, 5, TimeUnit.SECONDS);
            }
        } catch (RejectedExecutionException ex) {
            logger.debug("Executor shutting down; skipping initial notification check scheduling");
        }
        
        logger.debug("ActionItemCommandHandler initialized");
    }

    @Override
    public void shutdown() {
        logger.debug("Shutting down ActionItemCommandHandler...");
        shuttingDown = true;
        // Cancel scheduled tasks first
        try {
            if (dailyCheckFuture != null) {
                dailyCheckFuture.cancel(false);
            }
            if (initialCheckFuture != null) {
                initialCheckFuture.cancel(false);
            }
        } catch (Exception e) {
            logger.debug("Ignoring exception while cancelling scheduled tasks: {}", e.getMessage());
        }
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.debug("ActionItemCommandHandler shutdown complete");
    }

    @Override
    public String getHandlerName() {
        return "ActionItemCommandHandler";
    }



    private void scheduleDailyCheck() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next8AM = now.toLocalDate().atTime(LocalTime.of(8, 0));
        
        if (now.isAfter(next8AM)) {
            next8AM = next8AM.plusDays(1);
        }
        
        long minutesUntil8AM = ChronoUnit.MINUTES.between(now, next8AM);
        logger.debug("Scheduling daily check in {} minutes (at {})", minutesUntil8AM, next8AM);
        
        try {
            if (!shuttingDown && !scheduler.isShutdown()) {
                dailyCheckFuture = scheduler.scheduleAtFixedRate(
                    () -> {
                        try {
                            runDailyNotificationCheck(false);
                        } catch (Exception e) {
                            logger.error("Exception in scheduled daily check", e);
                        }
                    },
                    minutesUntil8AM,
                    24 * 60,
                    TimeUnit.MINUTES
                );
            }
        } catch (RejectedExecutionException ex) {
            logger.debug("Executor shutting down; skipping daily check scheduling");
        }
    }

    private void runDailyNotificationCheck(boolean testCommand) {
        runDailyNotificationCheckWithRetry(1, testCommand);
    }
    
    public void runDailyNotificationCheckWithRetry(int attempt, boolean testCommand) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime today8AM = now.toLocalDate().atTime(LocalTime.of(8, 0));
        LocalTime target8AM = LocalTime.of(8, 0);
        LocalTime currentTime = now.toLocalTime();
        
        // Check if it's too early (before 8:00 AM)
        if (currentTime.isBefore(target8AM) && !testCommand) {
            long secondsUntil8AM = java.time.Duration.between(currentTime, target8AM).getSeconds();
            logger.warn("⚠️ Scheduler triggered {} seconds early! Current: {}, Target: 8:00 AM", secondsUntil8AM, currentTime);
            
            if (attempt <= 5) {
                // Calculate more precise retry timing: wait until 8:00 AM or at least 30 seconds
                long retryDelaySeconds = Math.max(30, secondsUntil8AM);
                logger.debug("🔄 Rescheduling attempt #{} to run in {} seconds (attempt {}/5)", attempt + 1, retryDelaySeconds, attempt);
                try {
                    if (!shuttingDown && !scheduler.isShutdown()) {
                        scheduler.schedule(() -> runDailyNotificationCheckWithRetry(attempt + 1, false), retryDelaySeconds, TimeUnit.SECONDS);
                    } else {
                        logger.debug("Skipping reschedule attempt #{} - executor shutting down", attempt + 1);
                    }
                } catch (RejectedExecutionException ex) {
                    logger.debug("Executor shutting down; skipping reschedule attempt #{}", attempt + 1);
                }
                return;
            } else {
                logger.error("❌ Giving up after 5 attempts. Scheduler consistently running early - skipping notifications for today");
                return;
            }
        }
        
        int notificationsSent = 0;
        // Loop through each channel within each server and prepare notification buckets
        for (String serverId : persistence.getAllServers()) {
            for (String channelId : persistence.getAllChannels(serverId)) {
                List<ActionItem> items = persistence.getActionItemsForChannel(serverId, channelId);

                //determine if  notification is due for any items
                Map<ActionItem.Cadence, List<ActionItem>> itemsByCadence = new HashMap<>();
                for (ActionItem item : items) {
                    if (calculateNotificationDue(item, now, today8AM, testCommand)) {
                        itemsByCadence.computeIfAbsent(item.getCadence(),
                            k -> new ArrayList<>()).add(item);
                    }
                }
                for (Map.Entry<ActionItem.Cadence, List<ActionItem>> entry : itemsByCadence.entrySet()) {
                    ActionItem.Cadence cadence = entry.getKey();
                    List<ActionItem> itemsToPost = entry.getValue();
                    
                    logger.debug("Sending consolidated notification for {} cadence with {} items",
                        cadence, items.size());
                    sendConsolidatedNotification(serverId, channelId, cadence, itemsToPost);
                    
                    // Update all items in this cadence
                    if (!testCommand) {
                        for (ActionItem item : items) {
                            item.setLastNotified(now);
                        }
                    }
                    
                    notificationsSent += items.size();
                }
            }
        }

        persistence.saveCurrentState();
        logger.debug("Daily notification check completed. Consolidated notifications sent for {} items", 
                   notificationsSent);
    }
    
    private boolean calculateNotificationDue(ActionItem item, LocalDateTime now, LocalDateTime today8AM, boolean testCommand) {
        // For test commands, bypass cadence checks and always return true
        if (testCommand) {
            logger.debug("  Result: true (test command - bypassing cadence checks)");
            return true;
        }
        // Special case: If item was created today before 8 AM and we haven't notified after 8 AM yet
        // But only notify if it's the appropriate day for the cadence
        if (item.getLastNotified().toLocalDate().equals(now.toLocalDate()) && 
            item.getLastNotified().equals(item.getCreationTime()) && 
            item.getLastNotified().isBefore(today8AM)) {
            
            // Check if today is the right day for this cadence
            boolean shouldNotifyToday = false;
            switch (item.getCadence()) {
                case DAILY:
                    // Daily items can notify any day
                    shouldNotifyToday = true;
                    break;
                case WEEKLY:
                    // Weekly items only notify on Mondays
                    shouldNotifyToday = now.getDayOfWeek() == java.time.DayOfWeek.MONDAY;
                    break;
                case MONTHLY:
                    // Monthly items only notify on the 1st of the month
                    shouldNotifyToday = now.getDayOfMonth() == 1;
                    break;
            }
            
            if (shouldNotifyToday) {
                logger.debug("  Result: true (created today before 8 AM on correct day for {} cadence, first notification)", item.getCadence());
                return true;
            } else {
                logger.debug("  Result: false (created today before 8 AM but wrong day for {} cadence)", item.getCadence());
                return false;
            }
        }
        
        // Check if we've already notified today (after 8 AM)
        if (item.getLastNotified().toLocalDate().equals(now.toLocalDate()) && 
            !item.getLastNotified().isBefore(today8AM)) {
            logger.debug("  Result: false (already notified today after 8 AM Eastern)");
            return false;
        }
        
        // If we reach here, we haven't notified today after 8 AM
        // Check based on cadence from last notification
        switch (item.getCadence()) {
            case DAILY:
                // Daily: Every morning at 8am Eastern
                logger.debug("  Result: true (daily cadence, haven't notified today after 8 AM)");
                return true;
            case WEEKLY:
                // Weekly: Every Monday at 8am Eastern
                boolean isMonday = now.getDayOfWeek() == java.time.DayOfWeek.MONDAY;
                boolean weeklyDue = isMonday && (item.getLastNotified().toLocalDate().isBefore(now.toLocalDate()) || 
                    (item.getLastNotified().toLocalDate().equals(now.toLocalDate()) && item.getLastNotified().isBefore(today8AM)));
                logger.debug("  Result: {} (weekly cadence, isMonday: {}, lastNotified before today or before 8AM: {})", 
                    weeklyDue, isMonday, item.getLastNotified().toLocalDate().isBefore(now.toLocalDate()) || item.getLastNotified().isBefore(today8AM));
                return weeklyDue;
            case MONTHLY:
                // Monthly: Every 1st of the month at 8am Eastern
                boolean isFirstOfMonth = now.getDayOfMonth() == 1;
                boolean monthlyDue = isFirstOfMonth && (item.getLastNotified().toLocalDate().isBefore(now.toLocalDate()) || 
                    (item.getLastNotified().toLocalDate().equals(now.toLocalDate()) && item.getLastNotified().isBefore(today8AM)));
                logger.debug("  Result: {} (monthly cadence, isFirstOfMonth: {}, lastNotified before today or before 8AM: {})", 
                    monthlyDue, isFirstOfMonth, item.getLastNotified().toLocalDate().isBefore(now.toLocalDate()) || item.getLastNotified().isBefore(today8AM));
                return monthlyDue;
            default:
                logger.debug("  Result: false (unknown cadence)");
                return false;
        }
    }

    private void sendConsolidatedNotification(String serverId, String channelId, ActionItem.Cadence cadence, List<ActionItem> items) {
        if (items.isEmpty()) {
            return;
        }
        logger.debug("Starting sendConsolidatedNotification for {} cadence with {} items", cadence, items.size());
        
        if (jda == null) {
            logger.error("JDA is null, cannot send consolidated notification");
            return;
        }

        try {
            String consolidatedMessage = createConsolidatedNotificationMessage(cadence, items);
            
            jda.getTextChannelById(channelId).sendMessage(consolidatedMessage).queue(
                success -> {
                    logger.debug("✅ Successfully sent consolidated notification to channel {} for {} {} items", 
                            channelId, items.size(), cadence);
                }
            );
        } catch (Exception e) {
            logger.error("❌ Exception in sendConsolidatedNotification for channel {}: {}", 
                        channelId, e.getMessage(), e);
        }

    }

    private String createConsolidatedNotificationMessage(ActionItem.Cadence cadence, List<ActionItem> items) {
        StringBuilder message = new StringBuilder();
        message.append(String.format("🔔 **%s Action Item Reminders** %s\n\n", 
                                    cadence, getCadenceEmoji(cadence)));
        
        // Group items by user to show assignees clearly
        Map<String, List<ActionItem>> itemsByUser = items.stream()
            .collect(Collectors.groupingBy(ActionItem::getOwnerUserId));
        
        for (Map.Entry<String, List<ActionItem>> userEntry : itemsByUser.entrySet()) {
            String userId = userEntry.getKey();
            List<ActionItem> userItems = userEntry.getValue();
            
            message.append(String.format("**<@%s>:**\n", userId));
            for (ActionItem item : userItems) {
                message.append(String.format("• %s\n", item.getDescription()));
            }
            message.append("\n");
        }
        
        message.append("_Use `/actionitem list` to see all your items_");
        return message.toString();
    }

    private void handleAddCommand(SlashCommandInteractionEvent event) {
        String description = event.getOption("description").getAsString();
        String cadenceStr = event.getOption("cadence").getAsString();
        
        // Validate description is not empty
        if (description == null || description.trim().isEmpty()) {
            event.reply("❌ Description cannot be empty! Please provide a meaningful description for your action item.")
                .setEphemeral(true).queue();
            return;
        }
        
        // Validate description length
        if (description.length() > ActionItem.MAX_DESCRIPTION_LENGTH) {
            event.reply(String.format("❌ Description too long! Maximum length is %d characters. Your description is %d characters.", 
                ActionItem.MAX_DESCRIPTION_LENGTH, description.length()))
                .setEphemeral(true).queue();
            return;
        }
        
        try {
            ActionItem.Cadence cadence = ActionItem.Cadence.valueOf(cadenceStr);
            
            String ownerUserId = event.getUser().getId();
            String channelId = event.getChannel().getId();
            String serverId = event.getGuild().getId();
            
            ActionItem item = persistence.addActionItem(serverId, ownerUserId, channelId, description, cadence);
            
            event.reply(String.format("✅ Added action item: **%s** (%s)\nID: `%s`", 
                description, cadence, item.getId())).queue();
        } catch (IllegalArgumentException e) {
            // Check if it's a description validation error or cadence error
            if (e.getMessage().contains("Description cannot")) {
                event.reply("❌ " + e.getMessage()).setEphemeral(true).queue();
            } else {
                event.reply("❌ Invalid cadence. Must be one of: DAILY, WEEKLY, MONTHLY").setEphemeral(true).queue();
            }
        }
    }


    
    private void handleListCommand(SlashCommandInteractionEvent event) {
        String channelId = event.getChannel().getId();
        StringBuilder response = new StringBuilder("📋 **Action Items for this channel:**\n\n");
        boolean hasItems = false;

        String serverId = event.getGuild().getId();
        List<ActionItem> channelItems = persistence.getActionItemsForChannel(serverId, channelId);
        if (channelItems != null && !channelItems.isEmpty()) {
            logger.debug("Found {} action items in channel {}", channelItems.size(), channelId);
            channelItems.stream()
                .sorted((a, b) -> Integer.compare(Integer.parseInt(a.getId()), Integer.parseInt(b.getId())))
                .forEach(item -> {
                    String userName = BotUtilities.getUsernameByID(item.getOwnerUserId(), event.getGuild());
                    response.append(String.format("🔹 **#%s** - %s (%s %s) - *Assigned to: %s*\n", 
                        item.getId(), item.getDescription(), getCadenceEmoji(item.getCadence()), 
                        item.getCadence(), userName));
                });
            hasItems = true;
        }

        if (!hasItems) {
            response.append("No action items found in this channel. Use `/actionitem add` to create the first one!");
        }

        event.reply(response.toString()).queue();
    }

    private void handleRemoveCommand(SlashCommandInteractionEvent event) {
        String itemId = event.getOption("id").getAsString();
        String channelId = event.getChannel().getId();
        
        ActionItem item = persistence.getActionItem(event.getGuild().getId(), channelId, itemId);
        if (item == null) {
            event.reply("❌ Action item with ID `" + itemId + "` not found in this channel.").setEphemeral(true).queue();
            return;
        }
        
        String serverId = event.getGuild().getId();
        boolean removed = persistence.removeActionItem(serverId, channelId, itemId);
        if (removed) {
            event.reply(String.format("✅ Removed action item: **%s**", item.getDescription())).queue();
        } else {
            event.reply("❌ Failed to remove action item.").setEphemeral(true).queue();
        }
    }

    private void handleAssignCommand(SlashCommandInteractionEvent event) {
        String itemId = event.getOption("id").getAsString();
        User newUser = event.getOption("user").getAsUser();
        String channelId = event.getChannel().getId();
        
        ActionItem item = persistence.getActionItem(event.getGuild().getId(), channelId, itemId);
        if (item == null) {
            event.reply("❌ Action item with ID `" + itemId + "` not found in this channel.").setEphemeral(true).queue();
            return;
        }
        
        String newOwnerUserId = newUser.getId();
        
        String oldOwnerUserId = item.getOwnerUserId();
        String oldOwnerDisplayName = BotUtilities.getUsernameByID(oldOwnerUserId, event.getGuild());
        String newOwnerDisplayName = BotUtilities.getUsernameByID(newOwnerUserId, event.getGuild());
        
        boolean assigned = persistence.assignActionItem(event.getGuild().getId(), channelId, itemId, newOwnerUserId);
        if (assigned) {
            // Send detailed confirmation message
            String confirmationMessage = String.format(
                "✅ **Action Item Reassigned**\n\n" +
                "📝 **Item #%s:** %s\n" +
                "👤 **From:** %s\n" +
                "👤 **To:** %s\n\n" +
                "_The new owner will receive notifications for this item._",
                itemId, item.getDescription(), oldOwnerDisplayName, newOwnerDisplayName
            );
            event.reply(confirmationMessage).queue();
        } else {
            event.reply("❌ Failed to assign action item.").setEphemeral(true).queue();
        }
    }

    private void handleHelpCommand(SlashCommandInteractionEvent event) {
        String helpText = "🤖 **Action Item Bot Help**\n\n" +
                "**Commands:**\n" +
                "🌅 `/actionitem add <description> <cadence>` - Add a new action item\n" +
                "📋 `/actionitem list` - List your action items\n" +
                "🗑️ `/actionitem remove <id>` - Remove an action item by ID number\n" +
                "👤 `/actionitem assign <id> <@user>` - Assign an action item to another user\n" +
                "❓ `/actionitem help` - Show this help message\n\n" +
                "**Cadence Options:**\n" +
                "• `Daily` - Reminder every day at 8:00 AM Eastern\n" +
                "• `Weekly` - Reminder every Monday at 8:00 AM Eastern\n" +
                "• `Monthly` - Reminder on the 1st of each month at 8:00 AM Eastern\n\n" +
                "**Examples:**\n" +
                "`/actionitem add description:\"Take vitamins\" cadence:Daily`\n" +
                "`/actionitem assign id:5 user:@teammate`\n\n" +
                "**Note:** Action items have global IDs and can be reassigned to any user.";
        event.reply(helpText).setEphemeral(true).queue();
    }

    private String getCadenceEmoji(ActionItem.Cadence cadence) {
        switch (cadence) {
            case DAILY: return "🌅";
            case WEEKLY: return "📅";
            case MONTHLY: return "🗓️";
            default: return "⏰";
        }
    }

    private void loadDevelopmentConfig() {
        try {
            String envPath = ".env-variables";
            if (Files.exists(Paths.get(envPath))) {
                Files.lines(Paths.get(envPath))
                    .filter(line -> !line.trim().isEmpty() && !line.trim().startsWith("#"))
                    .forEach(line -> {
                        String[] parts = line.split("=", 2);
                        if (parts.length == 2) {
                            String key = parts[0].trim().toLowerCase();
                            String value = parts[1].trim();
                            if ("development_commands".equals(key)) {
                                developmentCommandsEnabled = "true".equalsIgnoreCase(value);
                                logger.debug("Development commands enabled: {}", developmentCommandsEnabled);
                            }
                        }
                    });
            }
        } catch (IOException e) {
            logger.warn("Failed to load development configuration: {}", e.getMessage());
        }
    }

    private void handleTestCommand(SlashCommandInteractionEvent event) {
        if (!developmentCommandsEnabled) {
            event.reply("❌ Development commands are not enabled. Set `DEVELOPMENT_COMMANDS=true` in .env-variables to use this command.")
                .setEphemeral(true).queue();
            return;
        }

        event.reply("🧪 **Development Test Command**\nForcing daily notification check...").setEphemeral(true).queue();
        
        // Run the daily notification check immediately
        try {
            if (!shuttingDown && !scheduler.isShutdown()) {
                scheduler.execute(() -> {
                    logger.debug("Running forced daily notification check from test command");
                    runDailyNotificationCheck(true);
                });
            } else {
                logger.warn("Scheduler is shutting down; cannot run test command's forced check");
            }
        } catch (RejectedExecutionException ex) {
            logger.warn("Rejected execution for test command run; executor is shutting down");
        }
    }
}
