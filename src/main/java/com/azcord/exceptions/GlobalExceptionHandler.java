package com.azcord.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;


@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InviteNotFoundException.class)
    public ResponseEntity<String> handleInviteNotFound(InviteNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(InviteExpiredException.class)
    public ResponseEntity<String> handleExpiredInvite(InviteExpiredException ex){
        return ResponseEntity.status(HttpStatus.GONE).body(ex.getMessage());
    }


    
}
