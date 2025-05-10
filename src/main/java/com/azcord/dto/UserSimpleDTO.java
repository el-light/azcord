package com.azcord.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A simplified DTO for representing users, e.g., message sender.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserSimpleDTO {
    private Long id;
    private String username;
    // Potentially avatar URL in the future

    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
}