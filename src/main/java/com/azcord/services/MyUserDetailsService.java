package com.azcord.services;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.azcord.models.User;
import com.azcord.repositories.UserRepository;

@Service
public class MyUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));

        List<GrantedAuthority> authorities;
        authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName())) 
                .collect(Collectors.toList());
        return org.springframework.security.core.userdetails.User.
                withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities(authorities)
                .build();
        
    }
}