package com.azcord.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReactionRequestDTO {
    @NotBlank(message = "Emoji cannot be blank")
    private String emojiUnicode; // The emoji character, e.g., "👍"
}