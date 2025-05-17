package com.azcord.dto;

import jakarta.validation.constraints.NotBlank;

public class InviteJoinDTO {
    @NotBlank
    private String code;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}