package com.azcord.controllers;

import com.azcord.config.FileStorageProperties;
import com.azcord.exceptions.FileStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Controller
@RequestMapping("/api/files") // Matches the base path used in LocalFileStorageService
public class FileController {

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);
    private final Path fileStorageLocation;

    public FileController(FileStorageProperties fileStorageProperties) {
        this.fileStorageLocation = Paths.get(fileStorageProperties.getUploadDir())
                .toAbsolutePath().normalize();
    }

    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename, HttpServletRequest request) {
        Path filePath = this.fileStorageLocation.resolve(filename).normalize();
        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                String contentType = null;
                try {
                    contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
                } catch (IOException ex) {
                    logger.info("Could not determine file type for: {}", filename);
                }
                if (contentType == null) {
                    contentType = "application/octet-stream"; // Default content type
                }

                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                        // Use "attachment" for HttpHeaders.CONTENT_DISPOSITION to force download
                        // Use "inline" to attempt to display in browser if possible
                        .body(resource);
            } else {
                logger.warn("File not found or not readable: {}", filename);
                // Do not throw FileStorageException here as it gives 500.
                // Return a 404 response directly.
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException ex) {
            logger.error("Malformed URL for file {}: {}", filename, ex.getMessage());
            return ResponseEntity.notFound().build(); // Or bad request
        }
    }
}