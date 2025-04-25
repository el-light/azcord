package com.azcord.exceptions;



public class DuplicateServerNameException extends RuntimeException {
    public DuplicateServerNameException( String message){
        super(message); 
    }
}
