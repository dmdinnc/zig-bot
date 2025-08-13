package com.discordbot.crossword;

import com.discordbot.BotCommandHandler;
import com.discordbot.BotUtilities;
import com.discordbot.crossword.persistence.CrosswordChannelPersistence;
import com.discordbot.crossword.persistence.CrosswordCompletionPersistence;
import com.discordbot.crossword.types.CrosswordCompletion;
import com.discordbot.crossword.types.FullCompletionRecord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Handles crossword completion tracking through message listening and slash commands.
 */
public class CrosswordCommandHandler extends ListenerAdapter implements BotCommandHandler {
    private static final Logger logger = LoggerFactory.getLogger(CrosswordCommandHandler.class);
    
    private final CrosswordCompletionPersistence completionPersistence;
    private final CrosswordChannelPersistence channelPersistence;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private JDA jda;
    private boolean developmentCommandsEnabled = false;
    
    // Pattern to find URLs in messages
    private static final Pattern URL_PATTERN = Pattern.compile(
        "https://www\\.nytimes\\.com/badges/games/mini\\.html\\?[^\\s]*"
    );
    
    public CrosswordCommandHandler() {
        this.completionPersistence = new CrosswordCompletionPersistence();
        this.channelPersistence = new CrosswordChannelPersistence();
        this.jda = null; // Will be set in initialize()
    }
    
    @Override
    public void initialize(JDA jda) {
        this.jda = jda;

        // Load development commands configuration
        loadDevelopmentConfig();

        // Schedule daily leaderboard posting
        scheduleDailyLeaderboard();

        // Schedule initial leaderboard check to run after JDA is fully ready (catch-up logic)
        scheduler.schedule(() -> {
            logger.debug("Running initial leaderboard check after JDA initialization");
            runDailyLeaderboardCheckWithRetry(1);
        }, 5, TimeUnit.SECONDS);

        logger.debug("CrosswordCommandHandler initialized");
    }
    
    public CrosswordCommandHandler(String dataFilePath) {
        this.completionPersistence = new CrosswordCompletionPersistence();
        this.channelPersistence = new CrosswordChannelPersistence();
        this.jda = null; // Will be set in initialize()
        
        // Persistence layer already loaded data during construction
        Set<String> serverIds = channelPersistence.getAllServerIds();
        logger.debug("Loaded {} servers with crossword completion data from {}", 
                   serverIds.size(), dataFilePath);
    }

    private void scheduleDailyLeaderboard() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next9AM = now.toLocalDate().atTime(LocalTime.of(8, 0));
        
        if (now.isAfter(next9AM)) {
            next9AM = next9AM.plusDays(1);
        }
        
        long minutesUntil9AM = ChronoUnit.MINUTES.between(now, next9AM);
        logger.debug("Scheduling daily leaderboard posting in {} minutes (at {})", minutesUntil9AM, next9AM);
        
        scheduler.scheduleAtFixedRate(
            this::runDailyLeaderboardCheck,
            minutesUntil9AM,
            24 * 60,
            TimeUnit.MINUTES
        );
    }
    
    private void runDailyLeaderboardCheck() {
        runDailyLeaderboardCheckWithRetry(1);
    }
    
    public void runDailyLeaderboardCheckWithRetry(int attempt) {
        LocalDateTime now = LocalDateTime.now();
        LocalTime target8AM = LocalTime.of(8, 0);
        LocalTime currentTime = now.toLocalTime();
        
        logger.debug("🏆 DAILY LEADERBOARD CHECK ATTEMPT #{} TRIGGERED at {}", attempt, now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        logger.debug("Current day of week: {}, day of month: {}", now.getDayOfWeek(), now.getDayOfMonth());
        logger.debug("Target time: 8:00 AM, Current time: {}", currentTime);
        
        // Check if it's too early (before 8:00 AM)
        if (currentTime.isBefore(target8AM)) {
            long secondsUntil8AM = java.time.Duration.between(currentTime, target8AM).getSeconds();
            logger.warn("⚠️ Scheduler triggered {} seconds early! Current: {}, Target: 8:00 AM", secondsUntil8AM, currentTime);
            
            if (attempt <= 5) {
                // Calculate more precise retry timing: wait until 8:00 AM or at least 30 seconds
                long retryDelaySeconds = Math.max(30, secondsUntil8AM);
                logger.debug("🔄 Rescheduling leaderboard attempt #{} to run in {} seconds (attempt {}/5)", attempt + 1, retryDelaySeconds, attempt);
                scheduler.schedule(() -> runDailyLeaderboardCheckWithRetry(attempt + 1), retryDelaySeconds, TimeUnit.SECONDS);
                return;
            } else {
                logger.error("❌ Giving up after 5 attempts. Scheduler consistently running early - skipping leaderboard for today");
                return;
            }
        }
        
        logger.debug("✅ Time check passed! Proceeding with leaderboard check");
        
        // Proceed with the actual leaderboard posting logic
        runDailyLeaderboardCheckInternal(false);
    }
    
    private void runDailyLeaderboardCheckInternal(boolean bypassDuplicateCheck) {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        logger.debug("Running daily leaderboard check for {} (bypass duplicate check: {})", yesterday, bypassDuplicateCheck);
        
        // Post leaderboard to all configured channels
        Map<String, List<String>> serverIds = channelPersistence.getAllConfigurations();
        for (String serverId : serverIds.keySet()) {
            for (String channelId : serverIds.get(serverId)) {
                // Check if we've already posted for this date (unless bypassing the check)
                if (!bypassDuplicateCheck && channelPersistence.hasPostedForDate(serverId, channelId, yesterday)) {
                    logger.debug("Leaderboard already posted for server {} on {}, skipping", serverId, yesterday);
                    continue;
                }

                // Get completions for this specific server
                List<CrosswordCompletion> serverCompletions = completionPersistence.getCompletions(serverId, channelId, yesterday);
                if (serverCompletions == null || serverCompletions.isEmpty()) {
                    logger.debug("No completions for server {} on {}, skipping", serverId, yesterday);
                    continue;
                }

                logger.debug("Posting leaderboard to channel {} for server {} on {}", 
                    channelId, serverId, yesterday);
                
                postLeaderboardToChannel(channelId, yesterday, serverCompletions);
                // Update the last posted date for this server
                if (!bypassDuplicateCheck) {
                    channelPersistence.updateLastPostedDate(serverId, channelId, yesterday);
                }
            }
        }
    }
    
    private void postLeaderboardToChannel(String channelId, LocalDate date, List<CrosswordCompletion> completions) {
        try {
            // Sort completions by time (fastest first)
            List<CrosswordCompletion> sortedEntries = completions.stream()
                .sorted((a, b) -> Integer.compare(a.getTimeInSeconds(), b.getTimeInSeconds()))
                .collect(Collectors.toList());
            
            // Create leaderboard message with user mentions for daily posts
            net.dv8tion.jda.api.entities.Guild guild = jda.getTextChannelById(channelId).getGuild();
            String leaderboardMessage = createLeaderboardMessage(date, sortedEntries, true, guild);
            
            // Send message to channel
            jda.getTextChannelById(channelId).sendMessage(leaderboardMessage).queue(
                success -> logger.debug("✅ Successfully posted daily leaderboard to channel {} for {}", channelId, date),
                error -> logger.error("❌ Failed to post daily leaderboard to channel {}: {}", channelId, error.getMessage(), error)
            );
            
        } catch (Exception e) {
            logger.error("Error posting leaderboard to channel {}: {}", channelId, e.getMessage(), e);
        }
    }
    
    private String createLeaderboardMessage(LocalDate date, List<CrosswordCompletion> sortedEntries, boolean mentionUsers, net.dv8tion.jda.api.entities.Guild guild) {
        StringBuilder message = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy");
        
        message.append("🏆 **Mini Crossword Leaderboard - ").append(date.format(formatter)).append("**\n\n");
        
        if (sortedEntries.isEmpty()) {
            message.append("No completions recorded for this date.");
            return message.toString();
        }
        
        // Add rankings
        for (int i = 0; i < sortedEntries.size(); i++) {
            CrosswordCompletion entry = sortedEntries.get(i);
            String userId = entry.getUserId();
            
            String medal = getMedalEmoji(i + 1);
            String timeFormatted = formatTime(entry.getTimeInSeconds());
            
            // Use mention or username based on the mentionUsers parameter
            String userDisplay = mentionUsers ? "<@" + userId + ">" : BotUtilities.getUsernameByID(userId, guild, jda);
            
            message.append(String.format("%s **#%d** - %s - %s\n", 
                medal, i + 1, userDisplay, timeFormatted));
        }
        
        message.append("\n*Great job everyone! 🎉*");
        message.append("\n\n🧩 **[Play Today's Mini Crossword](https://www.nytimes.com/crosswords/game/mini)**");
        return message.toString();
    }
    
    private String getMedalEmoji(int position) {
        switch (position) {
            case 1: return "🥇";
            case 2: return "🥈";
            case 3: return "🥉";
            default: return "🏅";
        }
    }
    
    private String formatTime(int timeInSeconds) {
        if (timeInSeconds < 60) {
            return timeInSeconds + "s";
        } else {
            int minutes = timeInSeconds / 60;
            int seconds = timeInSeconds % 60;
            return String.format("%dm %ds", minutes, seconds);
        }
    }

    private LocalDate parseFlexibleDate(String dateInput) {
        if (dateInput == null || dateInput.trim().isEmpty()) {
            throw new IllegalArgumentException("Date input is null or empty");
        }
        
        String trimmed = dateInput.trim();
        
        // Regex pattern: (\d{1,2}) - month (1-2 digits), ([/-]) - separator, (\d{1,2}) - day (1-2 digits), 
        // \2 - same separator as before, (\d{2}|\d{4}) - year (2 or 4 digits)
        Pattern datePattern = Pattern.compile("^(\\d{1,2})([/-])(\\d{1,2})\\2(\\d{2}|\\d{4})$");
        Matcher matcher = datePattern.matcher(trimmed);
        
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Date format not recognized: " + dateInput);
        }
        
        try {
            int month = Integer.parseInt(matcher.group(1));
            int day = Integer.parseInt(matcher.group(3));
            int year = Integer.parseInt(matcher.group(4));
            
            // Handle 2-digit years: 00-30 = 2000-2030, 31-99 = 1931-1999
            if (year < 100) {
                if (year <= 30) {
                    year += 2000;
                } else {
                    year += 1900;
                }
            }
            
            // Validate ranges
            if (day < 1 || day > 31) {
                throw new IllegalArgumentException("Invalid day: " + day);
            }
            if (month < 1 || month > 12) {
                throw new IllegalArgumentException("Invalid month: " + month);
            }
            if (year < 1900 || year > 2100) {
                throw new IllegalArgumentException("Invalid year: " + year);
            }
            
            return LocalDate.of(year, month, day);
            
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number format in date: " + dateInput);
        } catch (java.time.DateTimeException e) {
            throw new IllegalArgumentException("Invalid date: " + dateInput + " (" + e.getMessage() + ")");
        }
    }

    @Override
    public void shutdown() {
        // Shutdown scheduler
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // Save any pending data before shutdown
        completionPersistence.saveDataToStorage();
        
        // Save channel configuration
        channelPersistence.saveDataToStorage();
        
        logger.debug("CrosswordCommandHandler shutdown complete");
    }
    
    @Override
    public String getHandlerName() {
        return "CrosswordCommandHandler";
    }
    
    @Override
    public List<CommandData> getCommandData() {
        // Create base subcommands
        List<SubcommandData> subcommands = new ArrayList<>();
        subcommands.add(new SubcommandData("stats", "View your crossword completion statistics"));
        subcommands.add(new SubcommandData("recent", "View your recent completions")
            .addOption(OptionType.INTEGER, "count", "Number of recent completions to show (default: 5)", false));
        subcommands.add(new SubcommandData("best", "View your best (fastest) completion time"));
        subcommands.add(new SubcommandData("leaderboard", "View the leaderboard for fastest times")
        .addOption(OptionType.STRING, "date", "Date to show leaderboard for (D/M/YY, DD/MM/YYYY, D-M-YY, DD-MM-YYYY)", false));
        subcommands.add(new SubcommandData("setchannel", "Set the channel for crossword tracking (Admin only)")
            .addOption(OptionType.CHANNEL, "channel", "Channel to track crossword completions in", true));
        subcommands.add(new SubcommandData("help", "Show help information for crossword commands"));
        
        // Add test command only if development commands are enabled
        if (developmentCommandsEnabled) {
            subcommands.add(new SubcommandData("test", "Force daily leaderboard check (development only)"));
        }
        
        return List.of(Commands.slash("crossword", "Mini Crossword completion tracking commands")
            .addSubcommands(subcommands));
    }
    
    @Override
    public boolean handleCommand(SlashCommandInteractionEvent event) {
        if (!"crossword".equals(event.getName())) {
            return false;
        }
        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            event.reply("❌ Invalid crossword command").setEphemeral(true).queue();
            return true;
        }
        
        switch (subcommand) {
            case "stats" -> handleStatsCommand(event);
            case "recent" -> handleRecentCommand(event);
            case "best" -> handleBestCommand(event);
            case "leaderboard" -> handleLeaderboardCommand(event);
            case "setchannel" -> handleSetChannelCommand(event);
            case "help" -> handleHelpCommand(event);
            case "test" -> {
                if (developmentCommandsEnabled) {
                    handleTestCommand(event);
                } else {
                    event.reply("❌ Development commands are not enabled.").setEphemeral(true).queue();
                }
            }
            default -> event.reply("❌ Unknown crossword subcommand: " + subcommand).setEphemeral(true).queue();
        }
        
        return true;
    }

    /**
     * Listen for messages in the configured tracking channel and parse crossword URLs.
     */
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignore bot messages
        if (event.getAuthor().isBot()) {
            return;
        }
        
        // Only process messages in the configured tracking channel for this server
        String serverId = event.getGuild() != null ? event.getGuild().getId() : null;
        String channelId = event.getChannel().getId();
        
        if (serverId == null || !channelPersistence.isTrackingChannel(serverId, channelId)) {
            return;
        }
        
        String messageContent = event.getMessage().getContentRaw();
        
        // Find NYT Mini Crossword URLs in the message
        Matcher matcher = URL_PATTERN.matcher(messageContent);
        
        while (matcher.find()) {
            String url = matcher.group();
            
            // Parse the URL
            CrosswordUrlParser.CrosswordUrlData urlData = CrosswordUrlParser.parseUrl(url);
            
            if (urlData != null) {
                CrosswordCompletion completion = new CrosswordCompletion(event.getAuthor().getId(), urlData.getTimeInSeconds(), urlData.getOriginalUrl());
                // Create completion record
                completionPersistence.addCompletion(serverId, channelId, urlData.getDate(), completion);
                
                // React to the message to acknowledge tracking
                event.getMessage().addReaction(Emoji.fromUnicode("🧩")).queue();
                
                logger.debug("Successfully tracked crossword completion for user {} in server {} on {} with time {}s",
                            event.getAuthor().getId(), serverId, urlData.getDate(), urlData.getTimeInSeconds());

            }
        }
    }
    
    private void handleStatsCommand(SlashCommandInteractionEvent event) {
        // Check if command is executed in a guild (not DM)
        if (event.getGuild() == null) {
            event.reply("❌ This command can only be used in a server, not in direct messages.")
                .setEphemeral(true).queue();
            return;
        }
        
        String userId = event.getUser().getId();
        String serverId = event.getGuild().getId();
        String channelId = event.getChannel().getId();
        List<FullCompletionRecord> userCompletions =
            completionPersistence.getCompletionsForUser(serverId, channelId, userId);
        
        if (userCompletions.isEmpty()) {
            event.reply("📊 You haven't completed any crosswords yet! Share a NYT Mini Crossword completion link in the tracking channel to get started.")
                .setEphemeral(true).queue();
            return;
        }
        
        // Calculate statistics
        int totalCompletions = userCompletions.size();
        FullCompletionRecord bestTime = completionPersistence.getBestTime(serverId, channelId, userId);

        double averageTime = userCompletions.stream()
            .mapToInt(c -> c.getCompletion().getTimeInSeconds())
            .average()
            .orElse(0.0);
        
        String statsMessage = String.format(
            "📊 **Your Crossword Statistics**\n\n" +
            "🧩 **Total Completions:** %d\n" +
            "🏆 **Best Time:** %s (%s)\n" +
            "📈 **Average Time:** %d:%02d\n" +
            "📅 **Most Recent:** %s (%s)\n\n" +
            "_Use `/crossword recent` to see your recent completions!_",
            totalCompletions,
            bestTime != null ? bestTime.getCompletion().getFormattedTime() : "N/A",
            bestTime != null ? bestTime.getDate() : "",
            (int) averageTime / 60, (int) averageTime % 60,
            userCompletions.get(0).getDate(),
            userCompletions.get(0).getCompletion().getFormattedTime()
        );
        
        event.reply(statsMessage).setEphemeral(true).queue();
    }
    
    private void handleRecentCommand(SlashCommandInteractionEvent event) {
        // Check if command is executed in a guild (not DM)
        if (event.getGuild() == null) {
            event.reply("❌ This command can only be used in a server, not in direct messages.")
                .setEphemeral(true).queue();
            return;
        }
        
        String userId = event.getUser().getId();
        String channelId = event.getChannel().getId();
        String serverId = event.getGuild().getId();
        int count = event.getOption("count") != null ? 
            Math.min(event.getOption("count").getAsInt(), 10) : 5;
        
        List<FullCompletionRecord> recentCompletions = completionPersistence.getCompletionsForUser(serverId, channelId, userId);
        recentCompletions.subList(0, Math.min(count, recentCompletions.size()));
        
        if (recentCompletions.isEmpty()) {
            event.reply("📋 You haven't completed any crosswords yet! Share a NYT Mini Crossword completion link in the tracking channel to get started.")
                .setEphemeral(true).queue();
            return;
        }
        
        StringBuilder message = new StringBuilder();
        message.append(String.format("📋 **Your Recent Completions** (Last %d)\n\n", recentCompletions.size()));
        
        for (int i = 0; i < recentCompletions.size(); i++) {
            FullCompletionRecord completion = recentCompletions.get(i);
            message.append(String.format("%d. **%s** - %s (%s)\n",
                i + 1,
                completion.getDate(),
                completion.getCompletion().getFormattedTime(),
                completion.getDate().equals(LocalDate.now()) ? "Today" :
                completion.getDate().equals(LocalDate.now().minusDays(1)) ? "Yesterday" :
                completion.getDate().format(DateTimeFormatter.ofPattern("MMM dd"))
            ));
        }
        
        event.reply(message.toString()).setEphemeral(true).queue();
    }
    
    private void handleBestCommand(SlashCommandInteractionEvent event) {
        // Check if command is executed in a guild (not DM)
        if (event.getGuild() == null) {
            event.reply("❌ This command can only be used in a server, not in direct messages.")
                .setEphemeral(true).queue();
            return;
        }
        
        String userId = event.getUser().getId();
        String channelId = event.getChannel().getId();
        String serverId = event.getGuild().getId();
        FullCompletionRecord bestTime = completionPersistence.getBestTime(serverId, channelId, userId);
        
        if (bestTime == null) {
            event.reply("🏆 You haven't completed any crosswords yet! Share a NYT Mini Crossword completion link in the tracking channel to get started.")
                .setEphemeral(true).queue();
            return;
        }
        
        String bestMessage = String.format(
            "🏆 **Your Best Time**\n\n" +
            "⏱️ **Time:** %s\n" +
            "📅 **Date:** %s\n\n" +
            "_Keep practicing to beat your record!_",
            bestTime.getCompletion().getFormattedTime(),
            bestTime.getDate()
        );
        
        event.reply(bestMessage).setEphemeral(true).queue();
    }
    
    private void handleLeaderboardCommand(SlashCommandInteractionEvent event) {
        // Check if command is executed in a guild (not DM)
        if (event.getGuild() == null) {
            event.reply("❌ This command can only be used in a server, not in direct messages.")
                .setEphemeral(true).queue();
            return;
        }
        
        String serverId = event.getGuild().getId();
        String channelId = event.getChannel().getId();
        String dateInput = event.getOption("date") != null ? event.getOption("date").getAsString() : null;
        
        LocalDate targetDate;
        if (dateInput != null) {
            try {
                targetDate = parseFlexibleDate(dateInput);
            } catch (Exception e) {
                event.reply("❌ **Invalid date format!**\n\n" +
                           "Please use one of these formats:\n" +
                           "• `D/M/YY` (e.g., 8/9/25)\n" +
                           "• `D/M/YYYY` (e.g., 8/9/2025)\n" +
                           "• `D-M-YY` (e.g., 8-9-25)\n" +
                           "• `D-M-YYYY` (e.g., 8-9-2025)\n" +
                           "• `DD/MM/YY` (e.g., 08/09/25)\n" +
                           "• `DD/MM/YYYY` (e.g., 08/09/2025)")
                    .setEphemeral(true).queue();
                return;
            }
        } else {
            // Default to yesterday if no date provided
            targetDate = LocalDate.now().minusDays(1);
        }
        
        List<CrosswordCompletion> dateCompletions = completionPersistence.getCompletions(serverId, channelId, targetDate);
        
        if (dateCompletions.isEmpty()) {
            event.reply(String.format("📊 **No completions found for %s**\n\n" +
                                     "Try a different date or check if anyone completed the crossword that day.",
                                     targetDate.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))))
                .setEphemeral(true).queue();
            return;
        }
        
        // Sort completions by time (fastest first)
        List<CrosswordCompletion> sortedEntries = dateCompletions.stream()
            .sorted(Comparator.comparingInt(CrosswordCompletion::getTimeInSeconds))
            .collect(Collectors.toList());
        
        // Create leaderboard message without mentions for slash command
        String leaderboardMessage = createLeaderboardMessage(targetDate, sortedEntries, false, event.getGuild());
        
        event.reply(leaderboardMessage).setEphemeral(false).queue();
    }
    
    private void handleSetChannelCommand(SlashCommandInteractionEvent event) {
        // Check if command is executed in a guild (not DM)
        if (event.getGuild() == null) {
            event.reply("❌ This command can only be used in a server, not in direct messages.")
                .setEphemeral(true).queue();
            return;
        }
        
        // Check if user has permission (this is a simple check - you might want more sophisticated permission handling)
        if (!event.getMember().hasPermission(Permission.MANAGE_CHANNEL)) {
            event.reply("❌ You need the 'Manage Channels' permission to set the tracking channel.")
                .setEphemeral(true).queue();
            return;
        }
        
        String serverId = event.getGuild().getId();
        String channelId = event.getOption("channel").getAsChannel().getId();
        
        // Save channel configuration using the new server-based system
        channelPersistence.addTrackingChannel(serverId, channelId);
        
        event.reply(String.format("✅ Crossword tracking channel set to <#%s>!\n\n" +
                                 "Users can now share NYT Mini Crossword completion links in that channel to track their times.",
                                 channelId))
            .setEphemeral(true).queue();
        
        logger.debug("Crossword tracking channel set for server {} to channel {}", serverId, channelId);
    }
    
    private void handleHelpCommand(SlashCommandInteractionEvent event) {
        String helpMessage = """
            🧩 **Mini Crossword Tracker Help**
            
            **How to track completions:**
            1. Complete a NYT Mini Crossword
            2. Share the completion link in the configured tracking channel
            3. The bot will automatically track your time!
            
            **Available Commands:**
            • `/crossword stats` - View your completion statistics
            • `/crossword recent [count]` - View recent completions (default: 5)
            • `/crossword best` - View your fastest completion time
            • `/crossword leaderboard` - View server leaderboard (coming soon)
            • `/crossword help` - Show this help message
            
            **Admin Commands:**
            • `/crossword setchannel` - Set the tracking channel
            
            **Example completion link:**
            `https://www.nytimes.com/badges/games/mini.html?d=2025-08-11&t=163&c=...`
            
            _The bot extracts the date (d) and time in seconds (t) from these links._
            """;
        
        event.reply(helpMessage).setEphemeral(true).queue();
    }
    
    private void handleTestCommand(SlashCommandInteractionEvent event) {
        // Check if command is executed in a guild (not DM)
        if (event.getGuild() == null) {
            event.reply("❌ This command can only be used in a server, not in direct messages.")
                .setEphemeral(true).queue();
            return;
        }
        
        event.reply("🧪 **Running daily leaderboard test...**\n\nThis will force a leaderboard check for yesterday's completions.")
            .setEphemeral(true).queue();
        
        // Run the leaderboard check with bypass flag
        runDailyLeaderboardCheckInternal(true);
        
        logger.debug("Test command executed by user {} in server {}", event.getUser().getId(), event.getGuild().getId());
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


}
