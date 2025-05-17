package com.azcord.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;


 // Exception thrown when attempting to create a role with a name that already exists on the server.

@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateRoleNameException extends RuntimeException {
    public DuplicateRoleNameException(String message) {
        super(message);
    }
}