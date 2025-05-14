package com.azcord.services;

import com.azcord.models.User;
import com.azcord.dto.UserSimpleDTO;

/**
 * Utility class for consistent mapping between entities and DTOs.
 */
public class MapperUtil {
    
    /**
     * Converts a User entity to a UserSimpleDTO with all fields populated.
     * @param user The user entity to convert
     * @return A UserSimpleDTO with id, username, avatarUrl, and bio
     */
    public static UserSimpleDTO toSimple(User user) {
        if (user == null) return null;
        
        UserSimpleDTO dto = new UserSimpleDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setAvatarUrl(user.getAvatarUrl());
        dto.setBio(user.getBio());
        return dto;
    }
} 