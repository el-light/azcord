package com.azcord.exceptions;

public class ServerNotFoundException extends RuntimeException {

    public ServerNotFoundException(String message){
        super(message); 
    }
}
