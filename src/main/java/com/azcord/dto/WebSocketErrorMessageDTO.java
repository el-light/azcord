package com.azcord.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
public class WebSocketErrorMessageDTO {
    private String error;
    private String details;

    public WebSocketErrorMessageDTO(String title, String message) {
        this.error = title;
        this.details = message;
    }

}