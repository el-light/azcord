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

    // Return JSON 401 instead of redirect
    @Bean
    public AuthenticationEntryPoint customAuthenticationEntryPoint() {
        return new AuthenticationEntryPoint() {
            @Override
            public void commence(HttpServletRequest request, HttpServletResponse response,
                                 AuthenticationException authException) throws IOException, ServletException {
                System.out.println("Authentication failed: " + authException.getMessage()
                        + " for " + request.getRequestURI());

                String contentType = request.getContentType();
                boolean isMultipart = contentType != null
                        && contentType.toLowerCase().startsWith("multipart/");
                System.out.println("Is multipart request: " + isMultipart
                        + ", Content-Type: " + contentType);

                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                String accept = request.getHeader("Accept");
                if (isMultipart || (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE))) {
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter()
                            .write("{\"error\":\"Unauthorized\",\"message\":\""
                                    + authException.getMessage() + "\"}");
                } else {
                    response.setContentType(MediaType.TEXT_PLAIN_VALUE);
                    response.getWriter()
                            .write("Unauthorized: " + authException.getMessage());
                }
            }
        };
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh.authenticationEntryPoint(customAuthenticationEntryPoint()))
                .authorizeHttpRequests(auth -> auth
                        // allow preflight for your specific endpoints
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()

                        // permit JWT‐free access to your SockJS handshake + WS upgrade
                        .requestMatchers("/signal-ws/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()

                        // other public API paths
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/test/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/uploads/**").permitAll()
                        .requestMatchers("/*.html", "/*.js", "/*.css", "/*.svg", "/favicon.ico").permitAll()
                        .requestMatchers("/avatar-test.html", "/test-uploads.html",
                                "/upload-test.html", "/check-uploads.html").permitAll()
                        .requestMatchers("/images/**", "/assets/**", "/static/**").permitAll()

                        // everything else requires authentication
                        .anyRequest().authenticated()
                )
                // hook in your JWT filter
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(
                "http://127.0.0.1:5500",
                "http://localhost:5500",
                "null"
        ));
        configuration.setAllowedMethods(
                Arrays.asList("GET","POST","PUT","DELETE","OPTIONS","HEAD")
        );
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization","Content-Type","X-Requested-With",
                "accept","Origin","Access-Control-Request-Method",
                "Access-Control-Request-Headers"
        ));
        configuration.setExposedHeaders(Arrays.asList(
                "Access-Control-Allow-Origin","Access-Control-Allow-Credentials"
        ));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
