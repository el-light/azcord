package com.azcord.exceptions;


public class InviteNotFoundException extends RuntimeException {
    public InviteNotFoundException(String message) {
        super(message);
    }
}
