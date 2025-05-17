package com.azcord.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;


@Getter
@Setter
public class SendMessageDTO {
    @Size(max = 2000, message = "Message content cannot exceed 2000 characters")
    private String content;

    private Long channelId;
    private Long directMessageChatId;
    private Long parentMessageId;
    private transient List<MultipartFile> files;
    private List<String> attachmentUrls;
    private List<String> attachmentMimeTypes;

    public boolean isValidTarget() {
        return (channelId != null && directMessageChatId == null) || (channelId == null && directMessageChatId != null);
    }

    public boolean hasContent() {
        return (content != null && !content.isBlank()) ||
               (files != null && !files.isEmpty()) ||
               (attachmentUrls != null && !attachmentUrls.isEmpty());
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getChannelId() {
        return channelId;
    }

    public void setChannelId(Long channelId) {
        this.channelId = channelId;
    }

    public Long getDirectMessageChatId() {
        return directMessageChatId;
    }

    public void setDirectMessageChatId(Long directMessageChatId) {
        this.directMessageChatId = directMessageChatId;
    }

    public Long getParentMessageId() {
        return parentMessageId;
    }

    public void setParentMessageId(Long parentMessageId) {
        this.parentMessageId = parentMessageId;
    }

    public List<MultipartFile> getFiles() {
        return files;
    }

    public void setFiles(List<MultipartFile> files) {
        this.files = files;
    }

    public List<String> getAttachmentUrls() {
        return attachmentUrls;
    }

    public void setAttachmentUrls(List<String> attachmentUrls) {
        this.attachmentUrls = attachmentUrls;
    }

    public List<String> getAttachmentMimeTypes() {
        return attachmentMimeTypes;
    }

    public void setAttachmentMimeTypes(List<String> attachmentMimeTypes) {
        this.attachmentMimeTypes = attachmentMimeTypes;
    }
}