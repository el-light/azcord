package com.azcord.services;

import com.azcord.models.Attachment;
import com.azcord.models.Message;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;

public interface FileStorageService {
    /**
     * Stores a single file.
     * @param file The file to store.
     * @param message The message this file is attached to (for context, e.g., directory structure).
     * @return The created Attachment entity with file URL and metadata.
     * @throws IOException If an error occurs during file storage.
     */
    Attachment storeFile(MultipartFile file, Message message) throws IOException;

    /**
     * Stores multiple files.
     * @param files The list of files to store.
     * @param message The message these files are attached to.
     * @return A list of created Attachment entities.
     * @throws IOException If an error occurs during file storage.
     */
    List<Attachment> storeFiles(List<MultipartFile> files, Message message) throws IOException;

    /**
     * Deletes a file based on its stored path or URL.
     * @param filePath The path/URL of the file to delete.
     * @throws IOException If an error occurs during deletion.
     */
    void deleteFile(String filePath) throws IOException;

    // Potentially: Resource loadFileAsResource(String fileName);
}