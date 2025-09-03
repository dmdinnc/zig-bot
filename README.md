# Zig's Discord Bot

A multi-purpose Discord bot built with Java and JDA (Java Discord API) that provides action item management, crossword completion tracking, feedback collection, and image conversion utilities.

## Features

### 🎯 Action Items (`/ai` or `/actionitem`)
Manage personal action items with automated reminders:
- **Add**: Create action items with daily, weekly, or monthly reminders
- **List**: View all your active action items
- **Remove**: Delete action items by ID
- **Assign**: Assign action items to other users
- **Help**: Get detailed usage information

### 🧩 Crossword Tracking (`/crossword`)
Track and analyze Mini Crossword completion times:
- **Stats**: View your personal completion statistics
- **Recent**: See your recent completion times
- **Best**: Display your fastest completion time
- **Leaderboard**: View daily leaderboards with fastest times
- **Set Channel**: Configure crossword tracking channel (Admin only)
- **Help**: Get crossword command help

### 💬 Feedback System (`/feedback` & `/featurerequest`)
Submit feedback and feature requests:
- **Feedback**: Send general feedback about the bot
- **Feature Request**: Request new features or improvements

### 🖼️ Image Conversion (`/imagetogif`)
Convert images to GIF format:
- **Image to GIF**: Convert any image attachment to a single-frame GIF

## Commands Reference

### Action Items
```
/ai add <description> <cadence>
/ai list
/ai remove <id>
/ai assign <id> <user>
/ai help
/ai test (development only)
```

### Crossword Tracking
```
/crossword stats
/crossword recent [count]
/crossword best
/crossword leaderboard [date]
/crossword setchannel <channel>
/crossword help
/crossword test (development only)
```

### Feedback
```
/feedback <message> [category]
/featurerequest <message> [category]
```

### Image Conversion
```
/imagetogif <image_attachment>
```

## Local Development Setup

### Prerequisites
- **Java 17** or higher
- **Maven 3.6+**
- **Discord Bot Token** (from Discord Developer Portal)

### Environment Setup

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd windsurf-project
   ```

2. **Configure environment variables**
   
   Copy the example environment file:
   ```bash
   cp .env-variables.example .env-variables
   ```
   
   Edit `.env-variables` with your configuration:
   ```properties
   # Discord Bot Configuration
   bot.token=YOUR_BOT_TOKEN_HERE
   
   # Logging Configuration
   # Optional: Set log level (DEBUG, INFO, WARN, ERROR) (Default INFO)
   LOG_LEVEL=DEBUG
   
   # Optional: Register local testing commands
   DEVELOPMENT_COMMANDS=true
   
   # Feedback System Configuration
   # The server (guild) ID where feedback should be sent
   feedback.server.id=YOUR_SERVER_ID_HERE
   
   # The channel ID where feedback and feature requests should be sent
   feedback.channel.id=YOUR_FEEDBACK_CHANNEL_ID_HERE
   ```

3. **Build the project**
   ```bash
   mvn clean install
   ```

4. **Run the bot**
   ```bash
   ./launch.sh
   ```

### Development Scripts

- **`launch.sh`**: Builds and runs the Discord bot with proper logging
- **`launch-webex.sh`**: Builds and runs the Webex bot (Unix/macOS)

### Webex Bot (framework)

The repository includes a minimal Webex bot runner that starts a local webhook HTTP server. Feature integration will come later.

1. Copy the example environment file (if not already done):
   ```bash
   cp .env-variables.example .env-variables
   ```

2. Configure Webex settings in `.env-variables`:
   ```properties
   # Webex Bot Configuration
   webex.bot.token=YOUR_WEBEX_BOT_TOKEN_HERE
   webex.webhook.secret=YOUR_WEBEX_WEBHOOK_SECRET_HERE
   webex.port=8080
   ```

3. Build and run:
   - Unix/macOS:
     ```bash
     ./launch-webex.sh
     ```

4. Verify the bot is up by checking the health endpoint:
   - http://localhost:8080/healthz → should return `ok`

### Getting Discord Bot Token

1. Go to [Discord Developer Portal](https://discord.com/developers/applications)
2. Create a new application
3. Go to the "Bot" section
4. Create a bot and copy the token
5. Add the bot to your server with appropriate permissions:
   - Send Messages
   - Use Slash Commands
   - Embed Links
   - Attach Files
   - Read Message History

### Required Permissions

The bot requires the following Discord permissions:
- **Send Messages**: For sending notifications and responses
- **Use Slash Commands**: For all command functionality
- **Embed Links**: For rich message formatting
- **Attach Files**: For image conversion features
- **Read Message History**: For crossword completion tracking
- **Manage Messages**: For certain admin functions

### Data Storage
- **Action Items**: Stored in `action-items.json`
- **Crossword Data**: Stored in `crossword-completions-v2.json`
- **Channel Config**: Stored in `crossword-channels.json`

### Key Features
- **Scheduled Notifications**: Action items send automated reminders at 8 AM Eastern
- **Persistent Storage**: All data persists between bot restarts
- **Development Mode**: Special test commands available when `DEVELOPMENT_COMMANDS=true`
- **Modular Design**: Each feature is implemented as a separate command handler

## Configuration Options

### Environment Variables
- `bot.token`: Discord bot token (required)
- `LOG_LEVEL`: Logging verbosity (DEBUG, INFO, WARN, ERROR)
- `DEVELOPMENT_COMMANDS`: Enable test commands (true/false)
- `feedback.server.id`: Server ID for feedback messages
- `feedback.channel.id`: Channel ID for feedback messages

### Notification Scheduling
- **Daily Items**: Reminded every day at 8 AM Eastern
- **Weekly Items**: Reminded every Monday at 8 AM Eastern  
- **Monthly Items**: Reminded on the 1st of each month at 8 AM Eastern

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly with `DEVELOPMENT_COMMANDS=true`
5. Submit a pull request

## Troubleshooting

### Common Issues

**Bot not responding to commands:**
- Verify bot token is correct in `.env-variables`
- Check bot has proper permissions in Discord server
- Ensure bot is online and connected

**Notifications not working:**
- Check system timezone settings
- Verify action items are properly saved
- Review logs for scheduling errors

**Build failures:**
- Ensure Java 17+ is installed
- Verify Maven is properly configured
- Check for dependency conflicts

### Logs
The bot uses SLF4J for logging. Set `LOG_LEVEL=DEBUG` in `.env-variables` for detailed debugging information.

## License

This project is licensed under the MIT License - see the LICENSE file for details.
