package com.azcord.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component 
@ConfigurationProperties(prefix = "file")
public class FileStorageProperties {
    // Use the static resources directory which is automatically served by Spring Boot
    private String uploadDir = "src/main/resources/static/uploads";

    public String getUploadDir() {
        return uploadDir;
    }
    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }
}