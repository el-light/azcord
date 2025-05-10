package com.azcord.dto;

import jakarta.validation.constraints.NotBlank;

public class ReactionRequestDTO {
    @NotBlank(message = "Emoji cannot be blank")
    private String emojiUnicode; // The emoji character, e.g., "👍"

    public String getEmojiUnicode() {
        return emojiUnicode;
    }
}