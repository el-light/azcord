package com.azcord.services;

import com.azcord.models.Attachment;
import com.azcord.models.Message;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;

public interface FileStorageService {
    Attachment storeFile(MultipartFile file, Message message) throws IOException; // For message attachments
    List<Attachment> storeFiles(List<MultipartFile> files, Message message) throws IOException; // For message attachments

    /**
     * Stores a general public file (e.g., avatar, server icon) in the root upload directory.
     * @param file The file to store.
     * @return The publicly accessible URL of the stored file.
     * @throws IOException If an error occurs during file storage.
     */
    String storePublicFile(MultipartFile file) throws IOException;

    void deleteFile(String storedFileName) throws IOException;
}
