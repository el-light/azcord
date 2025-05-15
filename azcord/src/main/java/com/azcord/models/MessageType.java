package com.azcord.models;

/**
 * Type of message, e.g., regular text, image, video, or a system message.
 */
public enum MessageType {
    TEXT,
    IMAGE,
    VIDEO,
    FILE, // Generic file
    SYSTEM // For notifications like "User joined the channel"
}