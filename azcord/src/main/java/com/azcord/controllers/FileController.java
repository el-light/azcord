package com.azcord.controllers;

import com.azcord.config.FileStorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletRequest;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

@Controller
@RequestMapping("/api/files")
@CrossOrigin(origins = "*") // Allow access from any origin
public class FileController {

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);
    private final Path rootFileStorageLocation;

    public FileController(FileStorageProperties fileStorageProperties) {
        this.rootFileStorageLocation = Paths.get(fileStorageProperties.getUploadDir())
                .toAbsolutePath().normalize();
        logger.info("FileController initialized. Root storage location: {}", this.rootFileStorageLocation);
        
        // Create the directory if it doesn't exist
        try {
            Files.createDirectories(this.rootFileStorageLocation);
            logger.info("Created file storage directory (if it didn't exist): {}", this.rootFileStorageLocation);
            
            // Create a test image file
            createTestImage();
        } catch (Exception ex) {
            logger.error("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename, HttpServletRequest request) {
        // Basic sanitization to prevent path traversal.
        String sanitizedFilename = filename.replaceAll("\\.\\.[\\\\/]", "").replaceAll("^[\\\\/]+", "");

        if (sanitizedFilename.isEmpty() || sanitizedFilename.contains("..")) { // Double check after sanitization
            logger.warn("Invalid filename requested: {}", filename);
            return ResponseEntity.badRequest().build();
        }

        Path filePath = this.rootFileStorageLocation.resolve(sanitizedFilename).normalize();
        logger.debug("Attempting to serve file: {}", filePath);

        // Security check: Ensure the resolved path is still within the storage root
        if (!filePath.startsWith(this.rootFileStorageLocation)) {
            logger.warn("Attempt to access file outside of designated storage root: {}", filePath);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null); // Or a generic error resource
        }

        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                String contentType = null;
                try {
                    contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
                    if (contentType == null) { // Fallback using Files.probeContentType
                        contentType = Files.probeContentType(filePath);
                    }
                } catch (IOException ex) {
                    logger.info("Could not determine file type for {}: {}", filePath.getFileName().toString(), ex.getMessage());
                }
                if (contentType == null) {
                    contentType = "application/octet-stream"; // Default
                }

                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                        // Add CORS headers
                        .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                        .header(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET, OPTIONS")
                        .header(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type, Authorization")
                        .body(resource);
            } else {
                logger.warn("File not found or not readable: {}", filePath);
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException ex) {
            logger.error("Malformed URL for file path {}: {}", filePath, ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Error serving file {}: {}", filePath, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/create-test-image")
    public ResponseEntity<?> createTestImageEndpoint() {
        try {
            String filename = createTestImage();
            Map<String, String> response = new HashMap<>();
            response.put("message", "Test image created successfully");
            response.put("filename", filename);
            response.put("url", "/uploads/" + filename);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating test image", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error creating test image: " + e.getMessage());
        }
    }
    
    private String createTestImage() throws IOException {
        // Create a test image
        BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        
        // Fill background
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 200, 200);
        
        // Draw text
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 24));
        g.drawString("Test Image", 30, 100);
        
        // Add timestamp
        g.setFont(new Font("Arial", Font.PLAIN, 12));
        g.drawString(java.time.LocalDateTime.now().toString(), 20, 120);
        
        g.dispose();
        
        // Save to file
        String filename = "test-image-" + System.currentTimeMillis() + ".png";
        Path targetLocation = this.rootFileStorageLocation.resolve(filename);
        
        // Also save to static directory
        Path staticDir = Paths.get("src/main/resources/static/uploads").toAbsolutePath().normalize();
        Files.createDirectories(staticDir);
        Path staticLocation = staticDir.resolve(filename);
        
        ImageIO.write(image, "png", targetLocation.toFile());
        ImageIO.write(image, "png", staticLocation.toFile());
        
        logger.info("Created test image at: {}", targetLocation);
        logger.info("Also saved to static directory: {}", staticLocation);
        
        return filename;
    }
}