package com.azcord.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile; // For file uploads
import java.util.List;

/**
 * DTO for sending a new message.
 * Attachments will be handled as MultipartFile(s) if sent via REST,
 * or as URLs if sent via WebSocket (assuming pre-upload).
 * For simplicity in WebSocket, we might initially only support text messages or URLs.
 */
@Getter
@Setter
public class SendMessageDTO {
    @Size(max = 2000, message = "Message content cannot exceed 2000 characters")
    private String content; // Can be blank if only sending attachments

    private Long channelId; // Required if sending to a channel
    private Long directMessageChatId; // Required if sending to a DM chat

    private Long parentMessageId; // Optional: for replying to a message

    // For REST API that handles direct file uploads with the message
    // For WebSockets, files would typically be uploaded separately first, then their URLs sent.
    // This field might not be directly used in WebSocket message payload.
    private transient List<MultipartFile> files; // 'transient' so it's not serialized if not needed

    // If files are pre-uploaded and URLs are sent with the message payload (common for WebSockets)
    private List<String> attachmentUrls; // List of URLs for pre-uploaded attachments
    private List<String> attachmentMimeTypes; // Corresponding mime types for URLs

     // Validation: Either content or files/attachmentUrls must be present.
     // Either channelId or directMessageChatId must be present, but not both.
     public boolean isValidTarget() {
        return (channelId != null && directMessageChatId == null) || (channelId == null && directMessageChatId != null);
    }

    public boolean hasContent() {
        return (content != null && !content.isBlank()) ||
               (files != null && !files.isEmpty()) ||
               (attachmentUrls != null && !attachmentUrls.isEmpty());
    }
}