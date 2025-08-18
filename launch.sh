#!/bin/bash

echo "Discord Action Item Bot - Build & Launch"
echo "========================================"

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
    echo "Please create .env-variables file with your bot token:"
    echo ""
    echo "BOT_TOKEN=your_bot_token_here"
    echo "LOG_LEVEL=INFO"
    echo ""
    echo "Note: The .env-variables file is automatically ignored by git for security."
    exit 1
fi

echo ""
echo "Building Discord Action Item Bot..."

# Clean, compile, run tests, and package the project
echo "Running clean install (compile + test + package)..."
mvn clean install

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
echo "Starting bot..."
echo "Press Ctrl+C to stop the bot"
echo ""

# Run the standalone fat JAR
java -jar zig-bot.jar

if [ $? -ne 0 ]; then
    echo ""
    echo "Bot stopped with error code $?"
else
    echo ""
    echo "Bot stopped successfully"
fi
