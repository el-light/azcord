package com.azcord.dto;

import com.azcord.models.MessageType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentDTO {
    private Long id;
    private String fileName;
    private String fileUrl;
    private String mimeType;
    private Long fileSize;
    private MessageType attachmentType;
    private LocalDateTime uploadedAt;
}