package com.zigbot.features.imageconverter;

import com.zigbot.BotCommandHandler;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Command handler for converting images to single-frame GIFs
 */
public class ImageToGifCommandHandler implements BotCommandHandler {
    private static final Logger logger = LoggerFactory.getLogger(ImageToGifCommandHandler.class);
    private JDA jda;
    private volatile boolean shuttingDown = false;
    
    @Override
    public List<CommandData> getCommandData() {
        List<CommandData> commands = new ArrayList<>();
        
        commands.add(Commands.slash("imagetogif", "Convert an image to a single-frame GIF")
            .addOption(OptionType.ATTACHMENT, "image", "The image file to convert to GIF", true));
        
        return commands;
    }
    
    @Override
    public boolean handleCommand(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("imagetogif")) {
            return false;
        }
        
        // Guard against shutdown state
        try {
            if (shuttingDown || jda == null || jda.getStatus() == JDA.Status.SHUTTING_DOWN || jda.getStatus() == JDA.Status.SHUTDOWN) {
                event.reply("⚠️ Bot is shutting down. Please try again after it restarts.").setEphemeral(true).queue();
                return true;
            }
        } catch (Exception ex) {
            logger.debug("ImageToGif received command during shutdown; ignoring.");
            return true;
        }

        try {
            handleImageToGifCommand(event);
            return true;
        } catch (Exception e) {
            logger.error("Error handling imagetogif command", e);
            event.reply("❌ An error occurred while processing your image. Please try again.").setEphemeral(true).queue();
            return true;
        }
    }
    
    private void handleImageToGifCommand(SlashCommandInteractionEvent event) {
        // Defer the reply since image processing might take some time
        event.deferReply().queue();
        
        Message.Attachment attachment = event.getOption("image").getAsAttachment();
        
        // Validate that it's an image file
        if (!isValidImageFile(attachment)) {
            event.getHook().editOriginal("❌ Please provide a valid image file (PNG, JPG, JPEG, BMP, WEBP).").queue();
            return;
        }
        
        // Check file size (Discord has limits)
        if (attachment.getSize() > 8 * 1024 * 1024) { // 8MB limit
            event.getHook().editOriginal("❌ Image file is too large. Please use an image smaller than 8MB.").queue();
            return;
        }
        
        try {
            // Download the image
            logger.info("Processing image: {} ({})", attachment.getFileName(), attachment.getSize());
            
            BufferedImage image = downloadImage(attachment.getUrl());
            if (image == null) {
                event.getHook().editOriginal("❌ Failed to download or process the image. Please try again.").queue();
                return;
            }
            
            // Convert to single-frame GIF
            byte[] gifBytes = convertToSingleFrameGif(image);
            if (gifBytes == null) {
                event.getHook().editOriginal("❌ Failed to convert image to GIF format. Please try again.").queue();
                return;
            }
            
            // Generate output filename
            String originalName = attachment.getFileName();
            String gifName = getGifFileName(originalName);
            
            // Upload the GIF
            FileUpload fileUpload = FileUpload.fromData(gifBytes, gifName);
            
            event.getHook().editOriginal("✅ Successfully converted your image to a single-frame GIF!")
                .setFiles(fileUpload)
                .queue();
                
            logger.info("Successfully converted {} to GIF: {} bytes", originalName, gifBytes.length);
            
        } catch (Exception e) {
            logger.error("Error processing image to GIF conversion", e);
            event.getHook().editOriginal("❌ An error occurred while converting your image. Please try again.").queue();
        }
    }
    
    private boolean isValidImageFile(Message.Attachment attachment) {
        String fileName = attachment.getFileName().toLowerCase();
        return fileName.endsWith(".png") || 
               fileName.endsWith(".jpg") || 
               fileName.endsWith(".jpeg") || 
               fileName.endsWith(".bmp") || 
               fileName.endsWith(".webp");
    }
    
    private BufferedImage downloadImage(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            try (InputStream inputStream = url.openStream()) {
                return ImageIO.read(inputStream);
            }
        } catch (IOException e) {
            logger.error("Failed to download image from URL: {}", imageUrl, e);
            return null;
        }
    }
    
    private byte[] convertToSingleFrameGif(BufferedImage image) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            
            // Get GIF writer
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("gif");
            if (!writers.hasNext()) {
                logger.error("No GIF writers available");
                return null;
            }
            
            ImageWriter writer = writers.next();
            
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(outputStream)) {
                writer.setOutput(ios);
                
                // Prepare the image for GIF format (ensure it's compatible)
                BufferedImage gifCompatibleImage = convertToGifCompatibleImage(image);
                
                // Write the image as a single-frame GIF
                writer.write(gifCompatibleImage);
                
                // Ensure all data is written
                ios.flush();
                writer.dispose();
                
                byte[] result = outputStream.toByteArray();
                logger.info("Generated GIF with {} bytes", result.length);
                return result;
                
            } catch (Exception e) {
                logger.error("Error during GIF writing", e);
                writer.dispose();
                throw e;
            }
            
        } catch (IOException e) {
            logger.error("Failed to convert image to GIF", e);
            return null;
        }
    }
    
    /**
     * Convert image to GIF-compatible format (indexed color)
     */
    private BufferedImage convertToGifCompatibleImage(BufferedImage originalImage) {
        // Create a new image with TYPE_BYTE_INDEXED for GIF compatibility
        BufferedImage gifImage = new BufferedImage(
            originalImage.getWidth(), 
            originalImage.getHeight(), 
            BufferedImage.TYPE_BYTE_INDEXED
        );
        
        // Draw the original image onto the GIF-compatible image
        gifImage.getGraphics().drawImage(originalImage, 0, 0, null);
        gifImage.getGraphics().dispose();
        
        return gifImage;
    }
    
    private String getGifFileName(String originalFileName) {
        // Remove the original extension and add .gif
        int lastDotIndex = originalFileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return originalFileName.substring(0, lastDotIndex) + ".gif";
        } else {
            return originalFileName + ".gif";
        }
    }
    
    @Override
    public void initialize(JDA jda) {
        this.jda = jda;
        logger.info("ImageToGifCommandHandler initialized");
    }
    
    @Override
    public void shutdown() {
        shuttingDown = true;
        logger.info("ImageToGifCommandHandler shutting down");
    }
    
    @Override
    public String getHandlerName() {
        return "ImageToGifCommandHandler";
    }
}
