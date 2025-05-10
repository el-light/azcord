package com.azcord.dto;

import com.azcord.models.ChatType;
import java.time.LocalDateTime;
import java.util.Set;

public class DirectMessageChatDTO {
    private Long id;
    private ChatType chatType;
    private String name; // For group DMs
    private Set<UserSimpleDTO> participants;
    private MessageDTO lastMessage; // The most recent message in this DM chat
    private LocalDateTime createdAt;
    private LocalDateTime lastActivityAt;
    private int unreadCount; // Future: for unread message indicators

    public DirectMessageChatDTO() {}

    public DirectMessageChatDTO(Long id, ChatType chatType, String name, Set<UserSimpleDTO> participants,
                                MessageDTO lastMessage, LocalDateTime createdAt, LocalDateTime lastActivityAt,
                                int unreadCount) {
        this.id = id;
        this.chatType = chatType;
        this.name = name;
        this.participants = participants;
        this.lastMessage = lastMessage;
        this.createdAt = createdAt;
        this.lastActivityAt = lastActivityAt;
        this.unreadCount = unreadCount;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ChatType getChatType() {
        return chatType;
    }

    public void setChatType(ChatType chatType) {
        this.chatType = chatType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<UserSimpleDTO> getParticipants() {
        return participants;
    }

    public void setParticipants(Set<UserSimpleDTO> participants) {
        this.participants = participants;
    }

    public MessageDTO getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(MessageDTO lastMessage) {
        this.lastMessage = lastMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastActivityAt() {
        return lastActivityAt;
    }

    public void setLastActivityAt(LocalDateTime lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }
}