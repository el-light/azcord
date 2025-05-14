package com.azcord.dto;

import com.azcord.models.MessageType;
import java.time.LocalDateTime;

public class AttachmentDTO {
    private Long id;
    private String fileName;
    private String fileUrl;
    private String mimeType;
    private Long fileSize;
    private MessageType attachmentType;
    private LocalDateTime uploadedAt;

    public AttachmentDTO() {}

    public AttachmentDTO(Long id, String fileName, String fileUrl, String mimeType, Long fileSize, MessageType attachmentType, LocalDateTime uploadedAt) {
        this.id = id;
        this.fileName = fileName;
        this.fileUrl = fileUrl;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
        this.attachmentType = attachmentType;
        this.uploadedAt = uploadedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public MessageType getAttachmentType() {
        return attachmentType;
    }

    public void setAttachmentType(MessageType attachmentType) {
        this.attachmentType = attachmentType;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}