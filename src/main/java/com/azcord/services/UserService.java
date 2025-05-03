package com.azcord.services;

import javax.management.RuntimeErrorException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.azcord.models.User;
import com.azcord.repositories.UserRepository;

import jakarta.validation.constraints.Null;

@Service
public class UserService {

    @Autowired
    UserRepository userRepository; 

    @Autowired
    PasswordEncoder encoder; 

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
    
}
