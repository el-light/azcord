package com.azcord.dto;

import jakarta.validation.constraints.NotBlank;

public class RoleDTO {

    @NotBlank
    private String name; 

    private String color_Hex; 
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getColor_Hex() {
        return color_Hex;
    }
    
    public void setColor_Hex(String colourHex) {
        this.color_Hex = colourHex;
    }
}
