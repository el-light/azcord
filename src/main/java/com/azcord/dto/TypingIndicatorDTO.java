package com.azcord.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TypingIndicatorDTO {
    private Long userId;
    private String username;
    private Long channelId; // Null if DM
    private Long directMessageChatId; // Null if channel message
    private boolean isTyping; // true if typing, false if stopped
}