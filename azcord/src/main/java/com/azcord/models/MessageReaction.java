package com.azcord.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "message_reactions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"message_id", "user_id", "emoji_unicode"})
})
public class MessageReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // User who reacted

    @Column(name = "emoji_unicode", nullable = false) // Store the actual emoji string
    private String emojiUnicode; // e.g., "👍", "❤️"

    private LocalDateTime createdAt;

    // Default constructor needed by JPA
    public MessageReaction() {
    }
    
    // Constructor for creating a new reaction with message, user, and emoji
    public MessageReaction(Message message, User user, String emojiUnicode) {
        this.message = message;
        this.user = user;
        this.emojiUnicode = emojiUnicode;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getEmojiUnicode() {
        return emojiUnicode;
    }

    public void setEmojiUnicode(String emojiUnicode) {
        this.emojiUnicode = emojiUnicode;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MessageReaction that = (MessageReaction) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}