package com.azcord.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MessageReactionDTO {
    private Long userId; // ID of the user who reacted
    private String username; // Username of the user who reacted
    private String emojiUnicode; // The emoji character
    // private Long messageId; // Not needed if this DTO is part of MessageDTO
}