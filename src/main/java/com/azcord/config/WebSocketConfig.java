package com.azcord.config;

import com.azcord.security.JwtHandshakeInterceptor; // We'll create this
import com.azcord.services.JwtService;
import com.azcord.services.MyUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker // Enables WebSocket message handling, backed by a message broker.
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private MyUserDetailsService userDetailsService;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Defines message prefixes for messages bound for broker (e.g., to subscribe to topics)
        config.enableSimpleBroker("/topic", "/queue", "/user"); // "/user" for user-specific messages
        // Defines the prefix for messages bound for @MessageMapping annotated methods in controllers
        config.setApplicationDestinationPrefixes("/app");
        // Defines the prefix for user-specific destinations
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Registers the "/ws" endpoint, enabling SockJS fallback options so that alternate transports
        // can be used if WebSocket is not available.
        // SockJS is used to enable WebSocket emulation when actual WebSocket is not supported by the browser/network.
        registry.addEndpoint("/ws")
                .addInterceptors(new JwtHandshakeInterceptor(jwtService, userDetailsService)) // Add custom interceptor for JWT auth
                .setAllowedOriginPatterns("*") // Allow all origins for simplicity, configure appropriately for production
                .withSockJS();
    }

    // Optional: Configure message converters, interceptors, etc.
    // For example, to handle JWT authentication for WebSocket connections:
    // You would typically implement ChannelInterceptor and add it to the inboundChannel.
    // However, for STOMP over WebSocket, authentication is often handled at the HTTP handshake level.
    // We'll create a HandshakeInterceptor.
}