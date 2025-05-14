package com.azcord.security;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

import com.azcord.services.JwtService;
import com.azcord.services.MyUserDetailsService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;


@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService; 
    private final MyUserDetailsService userDetailsService; 

    public JwtAuthenticationFilter(JwtService jwtService, MyUserDetailsService userDetailsService)
    { this.jwtService = jwtService;
    this.userDetailsService = userDetailsService; 
}
    
    //Create filter
    @Override
    protected void doFilterInternal(HttpServletRequest req,
                            HttpServletResponse res,
                            FilterChain chain) throws ServletException, IOException {

        // 1.  Let pre-flight requests pass untouched
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            chain.doFilter(req, res);
            return;
        }

        String path = req.getServletPath(); 
        System.out.println("JwtAuthenticationFilter processing request: " + req.getMethod() + " " + path);
        System.out.println("Content-Type: " + req.getContentType());
        
        if (path.equals("/api/auth/login") ||
            path.equals("/api/auth/register") ||
            path.equals("/api/auth/refresh") ||
            path.startsWith("/ws")) { // If the path is for auth or WebSocket handshake utility calls
            System.out.println("Skipping JWT check for public endpoint: " + path);
            chain.doFilter(req, res); // Let it pass through without JWT header checks
            return;
        }

    
        String header = req.getHeader("Authorization"); 
        System.out.println("Authorization header: " + (header != null ? "present" : "missing"));
        
        boolean isAuthenticated = false;

        if(header != null && header.startsWith("Bearer ")){
            try {
                //my JWT token
                String token = header.substring(7); 
                System.out.println("Token length: " + token.length());

                //check whether the token is signed or expired
                boolean isExpired = jwtService.isTokenExpired(token);
                boolean isValidSignature = jwtService.isSignatureValid(token);
                System.out.println("Token expired: " + isExpired + ", Valid signature: " + isValidSignature);
                
                if(isExpired || !isValidSignature){
                    // Token is invalid or expired
                    System.out.println("Token validation failed: " + (isExpired ? "expired" : "invalid signature"));
                    SecurityContextHolder.clearContext();
                    // Mark request as having failed auth, so controllers can check this
                    req.setAttribute("jwt_authentication_failed", true);
                } else {
                    //take username, load userDetails, create Authentication object
                    String username = jwtService.extractUsername(token); 
                    System.out.println("Extracted username from token: " + username);

                    if(username != null && SecurityContextHolder.getContext().getAuthentication() == null){
                        UserDetails userDetails = userDetailsService.loadUserByUsername(username); 
                        System.out.println("Loaded user details for: " + username);

                        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                        //place authentication in SecurityContextHolder
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        isAuthenticated = true;
                        System.out.println("Authentication set in SecurityContext");
                    }
                }
            } catch(Exception e){
                System.out.println("Exception in JWT processing: " + e.getMessage());
                e.printStackTrace();
                SecurityContextHolder.clearContext();
                // Mark request as having failed auth, so controllers can check this
                req.setAttribute("jwt_authentication_failed", true);
                req.setAttribute("jwt_authentication_error", e.getMessage());
            }
        } else {
            // No Authorization header or invalid format
            System.out.println("No valid Authorization header found");
            SecurityContextHolder.clearContext();
            req.setAttribute("jwt_authentication_failed", true);
        }
        
        // Always continue with the filter chain
        System.out.println("Continuing filter chain, authenticated: " + isAuthenticated);
        chain.doFilter(req, res);
    }

}
