package com.azcord.dto;

import jakarta.validation.constraints.NotBlank;

public class ChannelDTO {

    private Long id;
    
    @NotBlank
    private String name;
    
    private String avatarUrl; // 🔥 NEW
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getAvatarUrl() {
        return avatarUrl;
    }
    
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}
