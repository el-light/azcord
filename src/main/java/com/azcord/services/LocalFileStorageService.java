package com.azcord.services;

import com.azcord.config.FileStorageProperties; // We'll create this next
import com.azcord.exceptions.FileStorageException;
import com.azcord.models.Attachment;
import com.azcord.models.Message;
import com.azcord.models.MessageType; // Ensure this is imported
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service("localFileStorageService") // Qualify if multiple implementations exist
public class LocalFileStorageService implements FileStorageService {

    private static final Logger logger = LoggerFactory.getLogger(LocalFileStorageService.class);
    private final Path fileStorageLocation;

    @Autowired
    public LocalFileStorageService(FileStorageProperties fileStorageProperties) {
        this.fileStorageLocation = Paths.get(fileStorageProperties.getUploadDir())
                .toAbsolutePath().normalize();
        logger.info("File storage location initialized to: {}", this.fileStorageLocation);
        try {
            Files.createDirectories(this.fileStorageLocation);
            logger.info("Created directory (if it didn't exist): {}", this.fileStorageLocation);
        } catch (Exception ex) {
            logger.error("Could not create the directory where the uploaded files will be stored.", ex);
            throw new FileStorageException("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    @Override
    public Attachment storeFile(MultipartFile file, Message message) throws IOException {
        if (file.isEmpty()) {
            throw new FileStorageException("Failed to store empty file.");
        }

        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        String fileExtension = "";
        int i = originalFileName.lastIndexOf('.');
        if (i > 0) {
            fileExtension = originalFileName.substring(i);
        }
        // Generate a unique file name to prevent conflicts
        String storedFileName = UUID.randomUUID().toString() + fileExtension;

        Path targetLocation = this.fileStorageLocation.resolve(storedFileName);
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
        logger.info("Stored file {} at {}", originalFileName, targetLocation);


        // Construct file URL for client access - this assumes files are served statically
        // You'll need to configure Spring MVC to serve files from this directory.
        // Example: /api/files/{filename}
        String fileDownloadUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/files/") // This path needs to be exposed by a controller
                .path(storedFileName)
                .toUriString();
        logger.info("File download URI: {}", fileDownloadUri);

        Attachment attachment = new Attachment();
        attachment.setMessage(message);
        attachment.setFileName(originalFileName);
        attachment.setFileUrl(fileDownloadUri); // Or just storedFileName if serving locally and path is known
        attachment.setMimeType(file.getContentType());
        attachment.setFileSize(file.getSize());
        attachment.setUploadedAt(LocalDateTime.now());
        attachment.setAttachmentType(determineAttachmentType(file.getContentType()));

        return attachment;
    }

    @Override
    public List<Attachment> storeFiles(List<MultipartFile> files, Message message) throws IOException {
        List<Attachment> attachments = new ArrayList<>();
        if (files != null) {
            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    attachments.add(storeFile(file, message));
                }
            }
        }
        return attachments;
    }

    @Override
    public void deleteFile(String storedFileName) throws IOException {
        // filePath here should be the unique stored file name (e.g., UUID.extension)
        // not the full URL. The service that calls this should extract the filename.
        try {
            Path filePath = this.fileStorageLocation.resolve(storedFileName).normalize();
             if (Files.exists(filePath)) {
                Files.delete(filePath);
                logger.info("Deleted file: {}", filePath);
            } else {
                logger.warn("Attempted to delete non-existent file: {}", filePath);
            }
        } catch (IOException ex) {
            logger.error("Could not delete file: {}. Reason: {}", storedFileName, ex.getMessage());
            throw new FileStorageException("Could not delete file " + storedFileName + ". Please try again!", ex);
        }
    }

    private MessageType determineAttachmentType(String mimeType) {
        if (mimeType == null) return MessageType.FILE;
        if (mimeType.startsWith("image/")) return MessageType.IMAGE;
        if (mimeType.startsWith("video/")) return MessageType.VIDEO;
        // Add more specific types if needed
        return MessageType.FILE;
    }
}