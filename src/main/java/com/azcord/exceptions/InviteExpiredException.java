package com.azcord.exceptions;

public class InviteExpiredException extends RuntimeException {
    
    public InviteExpiredException(String message){
        super(message);
    }
}
