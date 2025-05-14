package com.azcord.dto;

public class TypingIndicatorDTO {
    private Long userId;
    private String username;
    private Long channelId; // Null if DM
    private Long directMessageChatId; // Null if channel message
    private boolean isTyping; // true if typing, false if stopped

    public TypingIndicatorDTO() {
    }

    public TypingIndicatorDTO(Long userId, String username, Long channelId, Long directMessageChatId, boolean isTyping) {
        this.userId = userId;
        this.username = username;
        this.channelId = channelId;
        this.directMessageChatId = directMessageChatId;
        this.isTyping = isTyping;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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

    public boolean isTyping() {
        return isTyping;
    }

    public void setTyping(boolean typing) {
        isTyping = typing;
    }
}