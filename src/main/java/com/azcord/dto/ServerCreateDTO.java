package com.azcord.dto;

import jakarta.validation.constraints.NotBlank;


public class ServerCreateDTO {
    
    @NotBlank
    private String name; 
    

    public String getName() {
        return name;
    }   

    public void setName(String name) {
        this.name = name;
    }
}
