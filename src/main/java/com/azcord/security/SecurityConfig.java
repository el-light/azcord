package com.azcord.security;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.azcord.services.JwtService;
import com.azcord.services.MyUserDetailsService;


@Configuration
@EnableWebSecurity
@EnableMethodSecurity  
public class SecurityConfig {

    @Autowired
    MyUserDetailsService userDetailsService; 

    @Autowired
    JwtService jwtService; 

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter; 

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception{

        http
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/auth/**").permitAll()
            .requestMatchers("/error").permitAll()
            .requestMatchers("/ws/**").permitAll()
            .anyRequest().authenticated()
        )
        .addFilterBefore(jwtAuthenticationFilter, 
        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

        @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // IMPORTANT: Adjust the allowed origins to match your frontend development server.
        // "http://127.0.0.1:5500" is common for VS Code Live Server.
        // "null" is sometimes needed if you open the HTML file directly (file:// protocol)
        configuration.setAllowedOrigins(Arrays.asList("http://127.0.0.1:5500", "http://localhost:5500", "null"));
        configuration.setAllowedMethods(Arrays.asList("GET","POST", "PUT", "DELETE", "OPTIONS", "HEAD")); // Allow common methods
        // Allow all typical headers. You might need to add custom ones if your frontend sends them.
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With", "accept", "Origin", "Access-Control-Request-Method", "Access-Control-Request-Headers"));
        // Expose headers that the client might need to read
        configuration.setExposedHeaders(Arrays.asList("Access-Control-Allow-Origin", "Access-Control-Allow-Credentials"));
        configuration.setAllowCredentials(true); // Important if you plan to use cookies or sessions (though you're stateless with JWT)
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // Apply this configuration to all paths
        return source;
    }

    
}
