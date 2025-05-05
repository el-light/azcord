package com.azcord.dto;

import jakarta.validation.constraints.NotBlank;

public class RoleCreateDTO {

    @NotBlank
    private String name; 
    
    @NotBlank
    private String color_hex; 
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getColor_hex() {
        return color_hex;
    }
    
    public void setColor_hex(String colourHex) {
        this.color_hex = colourHex;
    }
}
