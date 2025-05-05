package com.azcord.dto;

import jakarta.validation.constraints.NotBlank;

public class UserRegistrationDTO {
    @NotBlank
    private String Username; 
    @NotBlank
    private String email;
    @NotBlank
    private String password; 

    public String getUsername() {
        return Username;
    }

    public void setUsername(String username) {
        this.Username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
