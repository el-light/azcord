package com.azcord.controllers;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;

import com.azcord.dto.UserLoginDTO;
import com.azcord.dto.UserRegistrationDTO;
import com.azcord.dto.UserSimpleDTO;
import com.azcord.exceptions.UserNotFoundException;
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

    //update user profile
    @PutMapping(value = "/users/me/profile", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<?> updateUserProfile(
        @RequestParam(value = "username", required = false) String username, 
        @RequestParam(value = "bio", required = false) String bio,
        @RequestParam(value = "avatar", required = false) MultipartFile avatar,
        Authentication authentication
    ){
        if(authentication == null || authentication.isAuthenticated() == false){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }

        String currentUsername = authentication.getName();

        try{
            UserSimpleDTO updatedUser = userService.updateUserProfile(currentUsername, username, avatar, bio);
            return ResponseEntity.ok(updatedUser);
        }catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to process avatar: " + e.getMessage());
        } catch (RuntimeException e) { // Catch other runtime exceptions like "username taken"
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @GetMapping("/users/me")
    public ResponseEntity<?> getCurrentUserProfile(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }

        String username = authentication.getName();
        User user = userService.getUserByName(username);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        }

        UserSimpleDTO userSimpleDTO = new UserSimpleDTO();
        userSimpleDTO.setId(user.getId());
        userSimpleDTO.setUsername(user.getUsername());

        return ResponseEntity.ok(userSimpleDTO);
    }

}