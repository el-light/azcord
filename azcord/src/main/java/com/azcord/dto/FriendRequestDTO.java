package com.azcord.dto;

import java.time.Instant;
import java.util.UUID;

import com.azcord.models.FriendRequest;
import com.azcord.services.MapperUtil;

public record FriendRequestDTO(UUID id, UserSimpleDTO sender,
                               UserSimpleDTO receiver, String status, Instant createdAt) {
    public FriendRequestDTO(FriendRequest fr) {
        this(fr.getId(), MapperUtil.toSimple(fr.getSender()),
             MapperUtil.toSimple(fr.getReceiver()),
             fr.getStatus().name(), fr.getCreatedAt());
    }
} 