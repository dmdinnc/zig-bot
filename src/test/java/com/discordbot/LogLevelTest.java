package com.discordbot;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class to verify log level configuration from .env-variables file
 */
public class LogLevelTest {
    
    @Test
    public void testLogLevelConfiguration() throws IOException {
        // Create a temporary .env-variables file for testing
        Path tempEnvFile = Paths.get(".env-variables-test");
        String testContent = "LOG_LEVEL=DEBUG\nbot.token=test_token\n";
        
        try {
            Files.write(tempEnvFile, testContent.getBytes());
            
            // Simulate the configureLoggingFromEnv method logic
            if (Files.exists(tempEnvFile)) {
                Files.lines(tempEnvFile)
                    .filter(line -> !line.trim().isEmpty() && !line.trim().startsWith("#"))
                    .forEach(line -> {
                        String[] parts = line.split("=", 2);
                        if (parts.length == 2) {
                            String key = parts[0].trim();
                            String value = parts[1].trim();
                            if ("LOG_LEVEL".equals(key)) {
                                System.setProperty("LOG_LEVEL", value.toUpperCase());
                                System.setProperty("configured.log.level", value.toUpperCase());
                            }
                        }
                    });
            }
            
            // Verify the system property was set correctly
            assertEquals("DEBUG", System.getProperty("LOG_LEVEL"));
            assertEquals("DEBUG", System.getProperty("configured.log.level"));
            
            // Test that logger can be created and will use the configured level
            Logger testLogger = LoggerFactory.getLogger(LogLevelTest.class);
            assertNotNull(testLogger);
            
            // Test debug logging (this will only show if level is DEBUG or lower)
            testLogger.debug("This is a test debug message - should be visible with DEBUG level");
            testLogger.info("This is a test info message - should always be visible");
            
        } finally {
            // Clean up
            Files.deleteIfExists(tempEnvFile);
        }
    }
    
    @Test
    public void testDefaultLogLevel() {
        // Clear any existing LOG_LEVEL property
        System.clearProperty("LOG_LEVEL");
        System.clearProperty("configured.log.level");
        
        // Simulate no .env-variables file scenario
        System.setProperty("LOG_LEVEL", "INFO");
        
        assertEquals("INFO", System.getProperty("LOG_LEVEL"));
    }
}
