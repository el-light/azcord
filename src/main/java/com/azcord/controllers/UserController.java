package com.azcord.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.azcord.dto.UserLoginDTO;
import com.azcord.dto.UserRegistrationDTO;
import com.azcord.models.User;
import com.azcord.services.JwtService;
import com.azcord.services.UserService;

@RestController
@RequestMapping("/api/auth")
public class UserController{

    @Autowired
    JwtService jwtService;

    @Autowired
    UserService userService; 
    
    //registration 
    @PostMapping("/register")
    @ResponseBody
    public User registerUser(@RequestBody UserRegistrationDTO userRegistrationDTO){
        
        return userService.register(userRegistrationDTO.getUsername(), 
        userRegistrationDTO.getEmail(), 
        userRegistrationDTO.getPassword());
    }
    
    //log in
    @PostMapping("/login")
    @ResponseBody
    public ResponseEntity<Object> login(@RequestBody UserLoginDTO userLoginDTO){

        //our login method from the service is called validateCredentials
        User user = userService.validateCredentials(userLoginDTO.getUsername(), userLoginDTO.getPassword()); 

        if(user == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        }

        String token = jwtService.createJWT(user); 
        return ResponseEntity.ok(token);
        
    }

}