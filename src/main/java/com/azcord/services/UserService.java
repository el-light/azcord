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

    @Transactional  
    public UserSimpleDTO updateUserProfile(String currentUsername, String newUsername, MultipartFile avatar, String bio){
        User user = userRepository.findByUsername(currentUsername).orElseThrow(() -> new UserNotFoundException("User"));
        if(StringUtils.hasText(newUsername) && !newUsername.equals(currentUsername)){
            if(userRepository.findByUsername(newUsername).isPresent()){
                throw new RuntimeException("Username " + newUsername +" already taken"); 
            }
            user.setUsername(newUsername);
        }

        if(bio != null){
            user.setBio(bio);
        }

        if(avatar!=null && !avatar.isEmpty()){
            try{
                String avatarUrl = fileStorageService.storeFile(avatar, null).getFileUrl(); 
                user.setAvatarUrl(avatarUrl);
            }catch(IOException e){
                throw new RuntimeException("Failed to store avatar"); 
            }
            
        }

        userRepository.save(user); 
        UserSimpleDTO usdto = new UserSimpleDTO();
        usdto.setId(user.getId());
        usdto.setUsername(user.getUsername());
        usdto.setAvatarUrl(user.getAvatarUrl());
        usdto.setBio(user.getBio());
        return usdto;
    }


    
}
