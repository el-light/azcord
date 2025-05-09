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
}