package com.azcord.security;

import java.util.Arrays;
import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.core.AuthenticationException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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
    
    // Custom authentication entry point to return 401 instead of redirecting to login
    @Bean
    public AuthenticationEntryPoint customAuthenticationEntryPoint() {
        return new AuthenticationEntryPoint() {
            @Override
            public void commence(HttpServletRequest request, HttpServletResponse response,
                                AuthenticationException authException) throws IOException, ServletException {
                // Log the authentication failure
                System.out.println("Authentication failed: " + authException.getMessage() + " for " + request.getRequestURI());
                
                // Check if this is a multipart request
                String contentType = request.getContentType();
                boolean isMultipart = contentType != null && contentType.toLowerCase().startsWith("multipart/");
                System.out.println("Is multipart request: " + isMultipart + ", Content-Type: " + contentType);
                
                // Return 401 status with JSON or text based on the accept header
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                
                if (isMultipart) {
                    // For multipart requests, always return JSON
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"" + authException.getMessage() + "\"}");
                } else if (request.getHeader("Accept") != null && 
                           request.getHeader("Accept").contains(MediaType.APPLICATION_JSON_VALUE)) {
                    // For requests accepting JSON
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"" + authException.getMessage() + "\"}");
                } else {
                    // For other requests
                    response.setContentType(MediaType.TEXT_PLAIN_VALUE);
                    response.getWriter().write("Unauthorized: " + authException.getMessage());
                }
            }
        };
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception{

        http
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(exceptionHandling -> 
            exceptionHandling.authenticationEntryPoint(customAuthenticationEntryPoint())
        )
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(org.springframework.http.HttpMethod.OPTIONS,"/api/users/me/profile").permitAll()
            .requestMatchers(org.springframework.http.HttpMethod.OPTIONS,"/**").permitAll()
            .requestMatchers("/api/auth/**").permitAll()
            .requestMatchers("/api/test/**").permitAll()
            .requestMatchers("/error").permitAll()
            .requestMatchers("/ws/**").permitAll()
            .requestMatchers("/uploads/**").permitAll()
            .requestMatchers("/*.html", "/*.js", "/*.css", "/*.svg", "/favicon.ico").permitAll()
            .requestMatchers("/avatar-test.html", "/test-uploads.html", "/upload-test.html", "/check-uploads.html").permitAll()
            .requestMatchers("/images/**", "/assets/**", "/static/**").permitAll()
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
