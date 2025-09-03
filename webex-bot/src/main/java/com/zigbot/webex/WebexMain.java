package com.zigbot.webex;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Minimal Webex bot bootstrap.
 * - Loads configuration from root-level .env-variables (same as Discord bot)
 * - Sets up basic logging based on LOG_LEVEL from .env-variables
 * - Starts a tiny HTTP server for receiving Webex webhooks (future feature work)
 */
public class WebexMain {
    private static Logger logger;

    public static void main(String[] args) {
        try {
            configureLoggingFromEnv();
            logger = LoggerFactory.getLogger(WebexMain.class);

            Properties config = loadConfig();
            configureLogging(config);

            String botToken = config.getProperty("webex.bot.token", "");
            String webhookSecret = config.getProperty("webex.webhook.secret", "");
            int port = Integer.parseInt(config.getProperty("webex.port", "8080"));

            if (botToken.isEmpty() || botToken.equals("YOUR_WEBEX_BOT_TOKEN_HERE")) {
                logger.warn("WEBEX bot token not configured. Set 'webex.bot.token' in .env-variables");
            }
            if (webhookSecret.isEmpty() || webhookSecret.equals("YOUR_WEBEX_WEBHOOK_SECRET_HERE")) {
                logger.warn("WEBEX webhook secret not configured. Set 'webex.webhook.secret' in .env-variables");
            }

            logger.info("Starting Webex webhook server on port {}...", port);
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/healthz", new TextHandler(200, "ok"));
            server.createContext("/webhook", new WebhookHandler(webhookSecret));
            server.setExecutor(null);
            server.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down Webex bot...");
                try {
                    server.stop(0);
                } catch (Exception ignored) {}
            }));

            logger.info("Webex bot is running. Press Ctrl+C to stop.");
        } catch (IOException e) {
            // Logger may not be initialized yet
            System.err.println("Failed to start Webex bot: " + e.getMessage());
            if (logger != null) logger.error("Failed to start Webex bot", e);
        }
    }

    private static Properties loadConfig() throws IOException {
        Properties config = new Properties();
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
                if (logger != null) logger.error("Failed to load .env-variables file", e);
            }
        } else {
            System.err.println(".env-variables file not found. Please create it with your WEBEX settings.");
        }
        return config;
    }

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
                                System.setProperty("LOG_LEVEL", value.toUpperCase());
                                System.setProperty("configured.log.level", value.toUpperCase());
                            }
                        }
                    });
            } else {
                System.setProperty("LOG_LEVEL", "INFO");
            }
        } catch (IOException e) {
            System.setProperty("LOG_LEVEL", "INFO");
            System.err.println("Failed to load logging configuration from .env-variables: " + e.getMessage());
        }
    }

    private static void configureLogging(Properties config) {
        String configuredLevel = System.getProperty("configured.log.level");
        if (configuredLevel != null) {
            // Logger may not yet be initialized on first call; safe to print to System.out
            if (logger != null) {
                logger.info("Log level set to: {}", configuredLevel);
            } else {
                System.out.println("Log level set to: " + configuredLevel);
            }
        }
    }

    private static class TextHandler implements HttpHandler {
        private final int code;
        private final String body;
        TextHandler(int code, String body) {
            this.code = code;
            this.body = body;
        }
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] resp = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        }
    }

    private static class WebhookHandler implements HttpHandler {
        private final String secret;
        WebhookHandler(String secret) { this.secret = secret == null ? "" : secret; }
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Placeholder for future: verify signature using `secret` and process JSON
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            logger.info("Received {} on {}", method, path);
            byte[] resp = "received".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        }
    }
}
