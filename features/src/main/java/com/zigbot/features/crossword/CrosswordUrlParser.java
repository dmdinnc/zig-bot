package com.zigbot.features.crossword;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

import java.util.regex.Pattern;

/**
 * Utility class for parsing LA Times Mini Crossword links and extracting data from messages.
 * Expected link pattern: https://www.latimes.com/games/mini-crossword?id=latimes-mini-YYYYMMDD&...
 */
public class CrosswordUrlParser {
    private static final Logger logger = LoggerFactory.getLogger(CrosswordUrlParser.class);
    
    // Pattern to match LA Times Mini Crossword URLs
    private static final Pattern LAT_MINI_PATTERN = Pattern.compile(
        "https://www\\.latimes\\.com/games/mini-crossword\\?.*"
    );
    
    private static final DateTimeFormatter LAT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    
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
     * Parses an LA Times Mini Crossword URL and extracts the date from the `id` parameter.
     * Example: id=latimes-mini-20250827 -> 2025-08-27. Returns null if parsing fails.
     */
    public static LocalDate parseLATimesDate(String url) {
        if (url == null || url.trim().isEmpty()) {
            logger.debug("URL is null or empty");
            return null;
        }
        if (!LAT_MINI_PATTERN.matcher(url).matches()) {
            logger.debug("URL does not match LA Times Mini pattern: {}", url);
            return null;
        }
        try {
            URI parsedUri = new URI(url);
            Map<String, String> queryParams = parseQueryParameters(parsedUri.getRawQuery());
            String id = queryParams.get("id");
            if (id == null) {
                logger.warn("Missing 'id' parameter in LA Times URL: {}", url);
                return null;
            }
            String digits = id.replaceAll("[^0-9]", "");
            if (digits.length() != 8) {
                logger.warn("Could not extract 8-digit date from id parameter: {}", id);
                return null;
            }
            try {
                LocalDate date = LocalDate.parse(digits, LAT_DATE_FORMATTER);
                logger.debug("Parsed LA Times date {} from id {}", date, id);
                return date;
            } catch (DateTimeParseException e) {
                logger.warn("Invalid LA Times date in id {}: {}", id, e.getMessage());
                return null;
            }
        } catch (Exception e) {
            logger.error("Error parsing LA Times crossword URL: {} - {}", url, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Extracts completion time (in seconds) from message text, e.g.:
     * "in 2 minutes and 13 seconds" or "in 13 seconds" or "in 2 minutes".
     * Returns -1 if not found.
     */
    public static int extractTimeFromText(String text) {
        if (text == null) return -1;
        String lower = text.toLowerCase();
        java.util.regex.Matcher mAndS = java.util.regex.Pattern
            .compile("\\b(\\d+)\\s+minutes?\\s+and\\s+(\\d+)\\s+seconds?\\b")
            .matcher(lower);
        if (mAndS.find()) {
            try {
                int minutes = Integer.parseInt(mAndS.group(1));
                int seconds = Integer.parseInt(mAndS.group(2));
                return minutes * 60 + seconds;
            } catch (NumberFormatException ignored) {}
        }
        java.util.regex.Matcher sOnly = java.util.regex.Pattern
            .compile("\\b(\\d+)\\s+seconds?\\b")
            .matcher(lower);
        if (sOnly.find()) {
            try {
                return Integer.parseInt(sOnly.group(1));
            } catch (NumberFormatException ignored) {}
        }
        java.util.regex.Matcher mOnly = java.util.regex.Pattern
            .compile("\\b(\\d+)\\s+minutes?\\b")
            .matcher(lower);
        if (mOnly.find()) {
            try {
                return Integer.parseInt(mOnly.group(1)) * 60;
            } catch (NumberFormatException ignored) {}
        }
        return -1;
    }
    // NYT parsing removed; LA Times is the only supported format.
}
