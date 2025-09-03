#!/bin/bash

echo "Webex Bot - Build & Launch"
echo "============================"

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "ERROR: Maven is not installed or not in PATH"
    echo "Please install Maven from https://maven.apache.org/download.cgi"
    echo "On Ubuntu/Debian: sudo apt install maven"
    echo "On macOS: brew install maven"
    exit 1
fi

# Check if .env-variables file exists
if [ ! -f ".env-variables" ]; then
    echo "ERROR: .env-variables file not found!"
    echo "Please create .env-variables file with your Webex settings:"
    echo ""
    echo "webex.bot.token=YOUR_WEBEX_BOT_TOKEN_HERE"
    echo "webex.webhook.secret=YOUR_WEBEX_WEBHOOK_SECRET_HERE"
    echo "LOG_LEVEL=INFO"
    echo ""
    echo "Note: The .env-variables file is automatically ignored by git for security."
    exit 1
fi

echo ""
echo "Building Webex Bot..."

# Build only the webex-bot module (and any required dependencies)
echo "Running clean install for webex-bot..."
mvn clean install -pl webex-bot -am

if [ $? -ne 0 ]; then
    echo "Build failed! This could be due to:"
    echo "  - Compilation errors"
    echo "  - Test failures"
    echo "  - Dependency issues"
    echo "Please check the output above for details."
    exit 1
fi

echo "Build and tests completed successfully!"
echo ""
echo "Starting Webex bot..."
echo "Press Ctrl+C to stop the bot"
echo ""

# Run the standalone fat JAR
java -jar webex-bot.jar

if [ $? -ne 0 ]; then
    echo ""
    echo "Webex bot stopped with error code $?"
else
    echo ""
    echo "Webex bot stopped successfully"
fi
