package com.azcord.dto;

import java.util.HashSet;
import java.util.Set;

import com.azcord.models.Permission;

import jakarta.validation.constraints.NotBlank;

public class RoleCreateDTO {

    @NotBlank
    private String name; 
    
    @NotBlank
    private String color_hex; 

    Set<Permission> permissions = new HashSet<>();

    public void setPermissions(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }
    
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
