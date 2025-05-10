package com.azcord.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component 
@ConfigurationProperties(prefix = "file")
public class FileStorageProperties {
    private String uploadDir = "./uploads"; // Default upload directory

    public String getUploadDir() {
        return uploadDir;
    }
    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }
}