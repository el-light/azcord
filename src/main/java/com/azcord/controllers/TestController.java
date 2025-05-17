package com.azcord.controllers;

import com.azcord.services.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @Autowired
    private FileStorageService fileStorageService;

    @PostMapping("/file-upload")
    public ResponseEntity<?> testUpload(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("File is empty");
            }

            System.out.println("Test upload received file: " + file.getOriginalFilename() + 
                              ", Size: " + file.getSize() + 
                              ", ContentType: " + file.getContentType());

            String fileUrl = fileStorageService.storePublicFile(file);
            System.out.println("Test upload stored file at: " + fileUrl);

            Map<String, String> response = new HashMap<>();
            response.put("fileUrl", fileUrl);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Failed to upload file: " + e.getMessage());
        }
    }

    @GetMapping("/check-uploads-dir")
    public ResponseEntity<?> checkUploadsDirectory() {
        // This method will be implemented to check if the uploads directory exists and is accessible
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Get the FileStorageService's upload directory
            String uploadDir = "src/main/resources/static/uploads";
            java.nio.file.Path path = java.nio.file.Paths.get(uploadDir);
            
            boolean exists = java.nio.file.Files.exists(path);
            boolean isDirectory = java.nio.file.Files.isDirectory(path);
            boolean isReadable = java.nio.file.Files.isReadable(path);
            boolean isWritable = java.nio.file.Files.isWritable(path);
            
            response.put("exists", exists);
            response.put("isDirectory", isDirectory);
            response.put("isReadable", isReadable);
            response.put("isWritable", isWritable);
            response.put("absolutePath", path.toAbsolutePath().toString());
            
            if (exists && isDirectory) {
                java.util.List<String> files = java.nio.file.Files.list(path)
                    .map(p -> p.getFileName().toString())
                    .collect(java.util.stream.Collectors.toList());
                response.put("files", files);
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
} 