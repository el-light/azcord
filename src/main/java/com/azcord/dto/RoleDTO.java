package com.azcord.dto;

import java.util.Set;

import com.azcord.models.Permission;

import jakarta.validation.constraints.NotBlank;

public class RoleDTO {

    private long id; 

    @NotBlank
    private String name; 

    private String color_Hex; 

    private Set<Permission> permissions; 

    public Set<Permission> getPermissions() { 
        return permissions; 
    }
    public void setPermissions(Set<Permission> permissions) { 
        this.permissions = permissions; 
    }
    

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
