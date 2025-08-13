package com.discordbot.crossword;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

import java.util.regex.Pattern;

/**
 * Utility class for parsing NYT Mini Crossword completion URLs.
 * Expected format: https://www.nytimes.com/badges/games/mini.html?d=2025-08-11&t=163&c=...&smid=url-share
 */
public class CrosswordUrlParser {
    private static final Logger logger = LoggerFactory.getLogger(CrosswordUrlParser.class);
    
    // Pattern to match NYT Mini Crossword URLs
    private static final Pattern NYT_MINI_PATTERN = Pattern.compile(
        "https://www\\.nytimes\\.com/badges/games/mini\\.html\\?.*"
    );
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    /**
     * Parses a NYT Mini Crossword URL and extracts completion data.
     * 
     * @param url The URL to parse
     * @return CrosswordUrlData containing parsed information, or null if parsing fails
     */
    public static CrosswordUrlData parseUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            logger.debug("URL is null or empty");
            return null;
        }
        
        // Check if URL matches NYT Mini pattern
        if (!NYT_MINI_PATTERN.matcher(url).matches()) {
            logger.debug("URL does not match NYT Mini pattern: {}", url);
            return null;
        }
        
        try {
            URL parsedUrl = new URL(url);
            Map<String, String> queryParams = parseQueryParameters(parsedUrl.getQuery());
            
            // Extract date (d parameter)
            String dateStr = queryParams.get("d");
            if (dateStr == null) {
                logger.warn("Missing 'd' parameter in URL: {}", url);
                return null;
            }
            
            LocalDate date;
            try {
                date = LocalDate.parse(dateStr, DATE_FORMATTER);
            } catch (DateTimeParseException e) {
                logger.warn("Invalid date format in URL: {} - {}", dateStr, e.getMessage());
                return null;
            }
            
            // Extract time (t parameter)
            String timeStr = queryParams.get("t");
            if (timeStr == null) {
                logger.warn("Missing 't' parameter in URL: {}", url);
                return null;
            }
            
            int timeInSeconds;
            try {
                timeInSeconds = Integer.parseInt(timeStr);
                if (timeInSeconds < 0) {
                    logger.warn("Invalid negative time in URL: {}", timeStr);
                    return null;
                }
            } catch (NumberFormatException e) {
                logger.warn("Invalid time format in URL: {} - {}", timeStr, e.getMessage());
                return null;
            }
            
            logger.debug("Successfully parsed crossword URL: date={}, time={}s", date, timeInSeconds);
            return new CrosswordUrlData(date, timeInSeconds, url);
            
        } catch (Exception e) {
            logger.error("Error parsing crossword URL: {} - {}", url, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Parses query parameters from a URL query string.
     */
    private static Map<String, String> parseQueryParameters(String query) {
        Map<String, String> params = new HashMap<>();
        
        if (query == null || query.trim().isEmpty()) {
            return params;
        }
        
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                try {
                    String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                    params.put(key, value);
                } catch (Exception e) {
                    logger.warn("Error decoding query parameter: {} - {}", pair, e.getMessage());
                }
            }
        }
        
        return params;
    }
    
    /**
     * Data class to hold parsed crossword URL information.
     */
    public static class CrosswordUrlData {
        private final LocalDate date;
        private final int timeInSeconds;
        private final String originalUrl;
        
        public CrosswordUrlData(LocalDate date, int timeInSeconds, String originalUrl) {
            this.date = date;
            this.timeInSeconds = timeInSeconds;
            this.originalUrl = originalUrl;
        }
        
        public LocalDate getDate() {
            return date;
        }
        
        public int getTimeInSeconds() {
            return timeInSeconds;
        }
        
        public String getOriginalUrl() {
            return originalUrl;
        }
        
        @Override
        public String toString() {
            return String.format("CrosswordUrlData{date=%s, timeInSeconds=%d, originalUrl='%s'}", 
                               date, timeInSeconds, originalUrl);
        }
    }
}
