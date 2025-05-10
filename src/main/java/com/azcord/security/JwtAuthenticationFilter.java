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


        String path = req.getServletPath(); 
        if (path.equals("/api/auth/login") ||
            path.equals("/api/auth/register") ||
            path.startsWith("/ws")) { // If the path is for auth or WebSocket handshake utility calls
            chain.doFilter(req, res); // Let it pass through without JWT header checks
            return;
        }

    
        String header = req.getHeader("Authorization"); 

        if(header != null && header.startsWith("Bearer ")){
            try {
                //my JWT token
                String token = header.substring(7); 

                //check whether the token is signed or expired
                if(jwtService.isTokenExpired(token) || !jwtService.isSignatureValid(token)){
                    chain.doFilter(req, res);
                    return;
                }
                
                //take username, load userDetails, create Authentication object
                String username = jwtService.extractUsername(token); 

                if(username!=null && SecurityContextHolder.getContext().getAuthentication() == null){
                
                UserDetails userDetails = userDetailsService.loadUserByUsername(username); 

                Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails,null, userDetails.getAuthorities());

                //place authentication in SecurityContextHolder
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
                } catch(Exception e){
                SecurityContextHolder.clearContext(); 
            }
        }
        //proceed with other filters in the chain
         chain.doFilter(req, res);
    }

}
