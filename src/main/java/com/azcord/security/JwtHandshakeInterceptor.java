package com.azcord.security;

import com.azcord.services.JwtService;
import com.azcord.services.MyUserDetailsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;


import java.security.Principal;
import java.util.List;
import java.util.Map;

public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(JwtHandshakeInterceptor.class);
    private final JwtService jwtService;
    private final MyUserDetailsService userDetailsService;

    public JwtHandshakeInterceptor(JwtService jwtService, MyUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

      @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        logger.debug("Attempting WebSocket handshake...");
        String token = null;
    
        if (request instanceof ServletServerHttpRequest) {
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
            // Try to get token from "Authorization" header (if client sends it, common for SockJS)
            String authHeader = servletRequest.getServletRequest().getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
                logger.debug("Token found in Authorization header.");
            } else {
                // Try to get token from query parameter (another common way for WebSockets)
                // e.g., ws://localhost:8080/ws?token=YOUR_JWT_TOKEN
                List<String> tokenParams = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().get("token");
                if (tokenParams != null && !tokenParams.isEmpty()) {
                    token = tokenParams.get(0);
                    logger.debug("Token found in query parameter 'token'.");
                }
            }
        }
    
        if (token != null) {
            try {
                if (!jwtService.isTokenExpired(token) && jwtService.isSignatureValid(token)) {
                    String username = jwtService.extractUsername(token);
                    if (username != null) {
                        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                        
                        // Create an Authentication object
                        UsernamePasswordAuthenticationToken auth = 
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                        
                        // 1. Make it the session's Principal
                        attributes.put("SPRING.SOCKET.PRINCIPAL", auth);
                        
                        // 2. Push it into the security context
                        SecurityContextHolder.getContext().setAuthentication(auth);
                        
                        logger.info("WebSocket handshake successful for user: {}", username);
                        return true; // Handshake approved
                    }
                }
            } catch (Exception e) {
                logger.error("WebSocket handshake authentication failed: {}", e.getMessage());
                return false; // Handshake denied
            }
        }
        logger.warn("WebSocket handshake denied: No valid token found.");
        // response.setStatusCode(HttpStatus.UNAUTHORIZED); // This doesn't work as expected for WS handshake rejections
        return false; // Handshake denied
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // Post-handshake logic (e.g., logging)
        if (exception != null) {
            logger.error("Exception after WebSocket handshake: {}", exception.getMessage());
        }
    }
}
