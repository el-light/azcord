package com.azcord.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;


@Data
public class ServerCreateDTO {
    
    @NotBlank
    private String name; 

    private String description; // Description of the server
    private String avatarUrl; // URL to the server's avatar image
    

    public String getName() {
        return name;
    }   

    public void setName(String name) {
        this.name = name;
    }
}
