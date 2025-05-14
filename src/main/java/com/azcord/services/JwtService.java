package com.azcord.services;



import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.azcord.models.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String SECRET_KEY;

    public String createJWT(User user){
        return Jwts.builder()
            .setSubject(user.getUsername())
            .claim("email", user.getEmail())
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 3600000))
            .signWith(SignatureAlgorithm.HS256, SECRET_KEY)
            .compact();
        }

//check if signature correct
    public boolean isSignatureValid(String token) {
        try {
            Jws<Claims> claims = Jwts.parser()
                .setSigningKey(SECRET_KEY)
                .parseClaimsJws(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    
//check if it is expired
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = Jwts.parser()
                .setSigningKey(SECRET_KEY)
                .parseClaimsJws(token)
                .getBody();

            return claims.getExpiration().before(new Date());
        } catch (JwtException e) {
            return true;
        }
    }

    //extract username
    public String extractUsername(String token){
        try{
            Claims claims = Jwts.parser()
                .setSigningKey(SECRET_KEY)
                .parseClaimsJws(token)
                .getBody();
            return claims.getSubject();
        }catch (JwtException e) {
            return null;
        }
    }

    // Helper method for test mode
    public boolean isInTestMode() {
        // We can implement logic to determine if we're in test mode
        // For now, let's return false (normal mode)
        return false;
    }

    
}
