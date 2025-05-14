package com.azcord.controllers;

import java.io.IOException;
import java.util.HashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
import jakarta.servlet.http.HttpServletRequest;

import com.azcord.dto.UserLoginDTO;
import com.azcord.dto.UserRegistrationDTO;
import com.azcord.dto.UserSimpleDTO;
import com.azcord.exceptions.UserNotFoundException;
import com.azcord.models.User;
import com.azcord.services.JwtService;
import com.azcord.services.UserService;
import com.azcord.services.FileStorageService;
import com.azcord.services.MapperUtil;

@RestController
@RequestMapping("/api")
public class UserController{

    @Autowired
    JwtService jwtService;

    @Autowired
    UserService userService; 
    
    @Autowired
    FileStorageService fileStorageService;
    
    @Autowired
    private SimpMessagingTemplate broker;
    
    //registration 
    @PostMapping("/auth/register")
    @ResponseBody
    public User registerUser(@RequestBody UserRegistrationDTO userRegistrationDTO){
        
        return userService.register(userRegistrationDTO.getUsername(), 
        userRegistrationDTO.getEmail(), 
        userRegistrationDTO.getPassword());
    }
    
    //log in
    @PostMapping("/auth/login")
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

    @PostMapping("/auth/refresh")
    public ResponseEntity<String> refreshToken(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }

        String username = authentication.getName();
        User user = userService.getUserByName(username);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        }

        // Generate a new token
        String newToken = jwtService.createJWT(user);
        return ResponseEntity.ok(newToken);
    }

    //update user profile
    @PutMapping(value = "/users/me/profile", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<?> updateUserProfile(
        @RequestParam(value = "username", required = false) String username, 
        @RequestParam(value = "bio", required = false) String bio,
        @RequestParam(value = "avatar", required = false) MultipartFile avatar,
        Authentication authentication,
        HttpServletRequest request
    ){
        System.out.println("\n==== PROFILE UPDATE REQUEST START ====");
        System.out.println("Request URI: " + request.getRequestURI());
        System.out.println("Content Type: " + request.getContentType());
        System.out.println("Authentication object present: " + (authentication != null));
        if (authentication != null) {
            System.out.println("Authentication name: " + authentication.getName());
            System.out.println("Authentication principal: " + authentication.getPrincipal());
            System.out.println("Is authenticated: " + authentication.isAuthenticated());
        }

        // Check if our filter flagged this request as having authentication failure
        Boolean authFailed = (Boolean) request.getAttribute("jwt_authentication_failed");
        System.out.println("jwt_authentication_failed attribute: " + authFailed);
        
        if (authFailed != null && authFailed) {
            String errorMsg = (String) request.getAttribute("jwt_authentication_error");
            System.err.println("JWT Authentication failed in profile update: " + (errorMsg != null ? errorMsg : "Unknown reason"));
            System.out.println("==== PROFILE UPDATE REQUEST END (Auth Failed) ====\n");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication failed: Invalid or expired token");
        }
        
        if(authentication == null || !authentication.isAuthenticated()){
            System.out.println("Authentication is null or not authenticated");
            System.out.println("==== PROFILE UPDATE REQUEST END (No Auth) ====\n");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }

        String currentUsername = authentication.getName();
        
        // Debug logging
        System.out.println("Updating profile for user: " + currentUsername);
        System.out.println("New username: " + username);
        System.out.println("New bio: " + (bio != null ? bio : "not provided"));
        System.out.println("Avatar provided: " + (avatar != null && !avatar.isEmpty()));
        
        if (avatar != null && !avatar.isEmpty()) {
            System.out.println("Avatar details - Name: " + avatar.getOriginalFilename() + 
                              ", Size: " + avatar.getSize() + 
                              ", ContentType: " + avatar.getContentType());
            try {
                // Test if we can read the avatar file content
                byte[] bytes = avatar.getBytes();
                System.out.println("Successfully read " + bytes.length + " bytes from avatar file");
            } catch (Exception e) {
                System.err.println("ERROR: Failed to read avatar file contents: " + e.getMessage());
                e.printStackTrace();
                System.out.println("==== PROFILE UPDATE REQUEST END (Avatar Read Error) ====\n");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Failed to read avatar file: " + e.getMessage());
            }
        }

        try{
            UserSimpleDTO updatedUser = userService.updateUserProfile(currentUsername, username, avatar, bio);
            System.out.println("Profile updated successfully. New avatarUrl: " + updatedUser.getAvatarUrl());
            
            // Broadcast the update to all connected clients
            broker.convertAndSend("/topic/users/updated", updatedUser);
            System.out.println("Broadcast profile update to: /topic/users/updated");
            
            System.out.println("==== PROFILE UPDATE REQUEST END (Success) ====\n");
            return ResponseEntity.ok(updatedUser);
        }catch (UserNotFoundException e) {
            System.err.println("User not found: " + e.getMessage());
            System.out.println("==== PROFILE UPDATE REQUEST END (User Not Found) ====\n");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IOException e) {
            System.err.println("Failed to process avatar: " + e.getMessage());
            e.printStackTrace();
            System.out.println("==== PROFILE UPDATE REQUEST END (IO Error) ====\n");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to process avatar: " + e.getMessage());
        } catch (RuntimeException e) { // Catch other runtime exceptions like "username taken"
            System.err.println("Runtime error: " + e.getMessage());
            e.printStackTrace();
            System.out.println("==== PROFILE UPDATE REQUEST END (Runtime Error) ====\n");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @GetMapping("/users/me")
    public ResponseEntity<?> getCurrentUserProfile(Authentication authentication) {
        System.out.println("\n==== GET USER PROFILE REQUEST START ====");
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                System.out.println("Authentication is null or not authenticated");
                System.out.println("==== GET USER PROFILE REQUEST END (Unauthorized) ====\n");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }

            String username = authentication.getName();
            System.out.println("Looking up user profile for: " + username);
            
            User user = userService.getUserByName(username);

            if (user == null) {
                System.out.println("User not found: " + username);
                System.out.println("==== GET USER PROFILE REQUEST END (Not Found) ====\n");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
            }
            
            System.out.println("User found. ID: " + user.getId() + ", Username: " + user.getUsername());
            System.out.println("Avatar URL: " + user.getAvatarUrl() + ", Has Bio: " + (user.getBio() != null));
            
            UserSimpleDTO dto = MapperUtil.toSimple(user);
            System.out.println("DTO created. ID: " + dto.getId() + ", Username: " + dto.getUsername());
            System.out.println("==== GET USER PROFILE REQUEST END (Success) ====\n");
            
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            System.err.println("Error in getCurrentUserProfile: " + e.getMessage());
            e.printStackTrace();
            System.out.println("==== GET USER PROFILE REQUEST END (Error) ====\n");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error retrieving user profile: " + e.getMessage());
        }
    }

    // Test endpoint for avatar uploads
    @PostMapping("/test/upload")
    public ResponseEntity<?> testFileUpload(@RequestParam("file") MultipartFile file) {
        System.out.println("Test file upload endpoint called");
        
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("No file provided");
        }
        
        System.out.println("Received file: " + file.getOriginalFilename() + 
                          ", Size: " + file.getSize() + 
                          ", ContentType: " + file.getContentType());
        
        try {
            String fileUrl = jwtService.isInTestMode() ? 
                "/uploads/test-avatar.svg" : // Use test avatar in test mode
                fileStorageService.storePublicFile(file);
            
            System.out.println("File saved successfully, URL: " + fileUrl);
            
            // Return success response with file URL
            return ResponseEntity.ok(new HashMap<String, String>() {{
                put("status", "success");
                put("message", "File uploaded successfully");
                put("fileUrl", fileUrl);
            }});
        } catch (Exception e) {
            System.err.println("Error saving test file: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new HashMap<String, String>() {{
                    put("status", "error");
                    put("message", "Failed to upload file: " + e.getMessage());
                }});
        }
    }

}