package com.azcord.services;

import com.azcord.config.FileStorageProperties;
import com.azcord.exceptions.FileStorageException;
import com.azcord.models.Attachment;
import com.azcord.models.Message;
import com.azcord.models.MessageType;
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

@Service("localFileStorageService")
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
        // This method remains for message-specific attachments if you differentiate their storage/metadata
        if (file.isEmpty()) {
            throw new FileStorageException("Failed to store empty file.");
        }
        String storedFileName = generateUniqueFileName(file.getOriginalFilename());
        Path targetLocation = this.fileStorageLocation.resolve(storedFileName);
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
        logger.info("Stored message attachment {} at {}", file.getOriginalFilename(), targetLocation);

        String fileDownloadUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/files/")
                .path(storedFileName)
                .toUriString();

        Attachment attachment = new Attachment();
        attachment.setMessage(message); // Link to message
        attachment.setFileName(StringUtils.cleanPath(file.getOriginalFilename()));
        attachment.setFileUrl(fileDownloadUri);
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
                    attachments.add(storeFile(file, message)); // Uses the above method
                }
            }
        }
        return attachments;
    }


    @Override
    public String storePublicFile(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new FileStorageException("Failed to store empty public file.");
        }
        String storedFileName = generateUniqueFileName(file.getOriginalFilename());
        Path targetLocation = this.fileStorageLocation.resolve(storedFileName); // Store in root uploadDir

        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
        logger.info("Stored public file {} as {} at {}", file.getOriginalFilename(), storedFileName, targetLocation);

        // Also save to static resources directory for direct access
        try {
            Path staticDir = Paths.get("src/main/resources/static/uploads").toAbsolutePath().normalize();
            Files.createDirectories(staticDir);
            Path staticLocation = staticDir.resolve(storedFileName);
            
            // Create a new input stream since the previous one was consumed
            Files.copy(file.getInputStream(), staticLocation, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Also saved to static directory: {}", staticLocation);
        } catch (Exception e) {
            logger.error("Error saving to static directory", e);
            // Continue even if this fails, as the main file was saved
        }

        // Use direct URL to static resource - no need for controller
        return "/uploads/" + storedFileName;
    }


    @Override
    public void deleteFile(String storedFileName) throws IOException {
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

    private String generateUniqueFileName(String originalFileName) {
        String cleanOriginalFileName = StringUtils.cleanPath(originalFileName);
        String fileExtension = "";
        int i = cleanOriginalFileName.lastIndexOf('.');
        if (i > 0) {
            fileExtension = cleanOriginalFileName.substring(i);
        }
        return UUID.randomUUID().toString() + fileExtension;
    }

    private MessageType determineAttachmentType(String mimeType) {
        if (mimeType == null) return MessageType.FILE;
        if (mimeType.startsWith("image/")) return MessageType.IMAGE;
        if (mimeType.startsWith("video/")) return MessageType.VIDEO;
        return MessageType.FILE;
    }
}