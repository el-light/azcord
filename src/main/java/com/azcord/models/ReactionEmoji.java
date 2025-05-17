package com.azcord.models;

import java.util.Arrays;
import java.util.Optional;

/**
 * Defines the allowed emojis for reactions.
 * The string representation should be the actual emoji character.
 */
public enum ReactionEmoji {
    THUMBS_UP("👍"),
    HEART("❤️"),
    PARTY_POPPER("🎉"),
    CRYING_FACE("😢"),
    LAUGHING_FACE("😂");
    // We will add more standard emojis here if needed in the future before "Nitro"

    private final String unicode;

    ReactionEmoji(String unicode) {
        this.unicode = unicode;
    }

    public String getUnicode() {
        return unicode;
    }

    public static Optional<ReactionEmoji>fromUnicode(String unicode) {
        return Arrays.stream(values())
                .filter(emoji -> emoji.getUnicode().equals(unicode))
                .findFirst();
    }
}