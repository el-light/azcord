package com.azcord.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class ForbiddenException extends RuntimeException {
    
    public ForbiddenException() {
        super("You do not have permission to perform this action");
    }
    
    public ForbiddenException(String message) {
        super(message);
    }
} 