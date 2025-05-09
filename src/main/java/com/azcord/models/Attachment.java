package com.azcord.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "attachments")
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @Column(nullable = false)
    private String fileName; // Original file name

    @Column(nullable = false)
    private String fileUrl; // URL or path to the stored file

    @Column(nullable = false)
    private String mimeType; //"image/jpeg", "video/mp4"

    private Long fileSize; // Size in bytes

    private LocalDateTime uploadedAt;

    @Enumerated(EnumType.STRING)
    private MessageType attachmentType; // Redundant if mimeType is good, but can be useful for quick filtering (IMAGE, VIDEO, FILE)

    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
    }
}