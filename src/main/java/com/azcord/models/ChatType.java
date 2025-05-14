package com.azcord.models;

public enum ChatType {
    DIRECT_MESSAGE, // 1-on-1
    GROUP_DIRECT_MESSAGE // Multiple users, not a server channel
    // CHANNEL type is implicitly handled by Message.channel != null
}