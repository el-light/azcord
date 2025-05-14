package com.azcord.services;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.azcord.dto.UserSimpleDTO;
import com.azcord.exceptions.UserNotFoundException;
import com.azcord.models.User;
import com.azcord.repositories.UserRepository;
import com.azcord.services.MapperUtil;

import jakarta.transaction.Transactional;

@Service
public class UserService {

    @Autowired
    UserRepository userRepository; 

    @Autowired
    PasswordEncoder encoder; 

    @Autowired
    private FileStorageService fileStorageService;

    public User register(String username,String email, String password){

        if(userRepository.findByUsername(username).isPresent()){
            throw new RuntimeException("Username is already taken"); 
        }
        if(userRepository.findByEmail(email).isPresent()){
            throw new RuntimeException("User with such email already exists"); 
        }

        User user = new User(); 
        user.setEmail(email);
        user.setUsername(username);
        user.setPassword(encoder.encode(password)); 

        return userRepository.save(user); 
    }

    public User validateCredentials(String username, String password){

        if(!userRepository.findByUsername(username).isPresent()){
            return null; 
        }
       
        User user = userRepository.findByUsername(username).get();
        if(encoder.matches(password, user.getPassword())){
            return user; 
        }else{
            return null; 
        }
        
    }

    public User getUserByName(String username){
        return userRepository.findByUsername(username).orElse(null);
    }

    public User getUserById(Long id){
        return userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User Not Found")); 
    }

    /**
     * Returns the ID of a user given their username
     */
    public Long idOf(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new UserNotFoundException("User " + username + " not found"))
            .getId();
    }

@Transactional
public UserSimpleDTO updateUserProfile(String currentUsername, String newUsername, MultipartFile avatar, String bio) throws IOException { // Added throws IOException
    User user = userRepository.findByUsername(currentUsername)
            .orElseThrow(() -> new UserNotFoundException("User " + currentUsername + " not found."));

    if (StringUtils.hasText(newUsername) && !newUsername.equals(currentUsername)) {
        if (userRepository.findByUsername(newUsername).isPresent()) {
            throw new RuntimeException("Username '" + newUsername + "' is already taken.");
        }
        user.setUsername(newUsername);
    }

    // Assuming User entity has setBio and setAvatarUrl methods
    if (bio != null) { // Allow setting bio to empty string
        user.setBio(bio);
    }

    if (avatar != null && !avatar.isEmpty()) {
        String avatarUrl = fileStorageService.storePublicFile(avatar); // Simplified call
        user.setAvatarUrl(avatarUrl);
    }

    User savedUser = userRepository.save(user);

    return MapperUtil.toSimple(savedUser);
}


    
}
