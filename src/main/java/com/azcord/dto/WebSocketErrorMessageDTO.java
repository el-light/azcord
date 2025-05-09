package com.azcord.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class WebSocketErrorMessageDTO {
    private String error;
    private String details;
}