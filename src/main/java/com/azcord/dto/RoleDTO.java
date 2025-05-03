package com.azcord.dto;

import jakarta.validation.constraints.NotBlank;

public class RoleDTO {

    private long id; 

    @NotBlank
    private String name; 

    private String color_Hex; 

    public long getId() {
        return id;
    }  
    
    public void setId(long id) {
        this.id = id;
    }
    
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
