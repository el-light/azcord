package com.azcord.dto;

import com.azcord.models.ChatType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DirectMessageChatDTO {
    private Long id;
    private ChatType chatType;
    private String name; // For group DMs
    private Set<UserSimpleDTO> participants;
    private MessageDTO lastMessage; // The most recent message in this DM chat
    private LocalDateTime createdAt;
    private LocalDateTime lastActivityAt;
    private int unreadCount; // Future: for unread message indicators
}