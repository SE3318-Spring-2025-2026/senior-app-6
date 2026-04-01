package com.senior.spm.service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.senior.spm.entity.StaffUser;
import com.senior.spm.entity.Student;
import com.senior.spm.repository.StaffUserRepository;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;

@Service
public class JWTService {

    @Value("${jwt.secret}")
    private String SECRET_KEY;

    @Value("${jwt.expiration}")
    private long EXPIRATION_TIME;

    private final StaffUserRepository staffUserRepository;

    @PostConstruct
    @SuppressWarnings("unused")
    private void validateSecret() throws IllegalStateException {
        try {
            var keyBytes = Decoders.BASE64.decode(SECRET_KEY);
            if (keyBytes.length < 32) {
                throw new IllegalStateException("jwt.secret must be at least 32 bytes after base64 decoding");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("jwt.secret must be a valid base64 encoded string");
        }
    }

    public JWTService(StaffUserRepository staffUserRepository) {
        this.staffUserRepository = staffUserRepository;
    }

    public String issueToken(StaffUser staffUser) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", staffUser.getId());
        claims.put("mail", staffUser.getMail());
        claims.put("role", staffUser.getRole());
        return Jwts.builder()
                .claims(claims)
                .subject(StaffUser.class.getSimpleName())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(getSecretKey())
                .compact();
    }

    public String issueToken(Student student) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", student.getId());
        claims.put("githubUsername", student.getGithubUsername());
        claims.put("role", "Student");
        return Jwts.builder()
                .claims(claims)
                .subject(Student.class.getSimpleName())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(getSecretKey())
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            var claims = getClaims(token);
            var subject = claims.getSubject();
            if (StaffUser.class.getSimpleName().equals(subject)) {
                var idString = claims.get("id", String.class);
                if (idString == null) {
                    return false;
                }
                var id = UUID.fromString(idString);
				return !staffUserRepository.findById(id).isEmpty();
            }
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Claims getClaims(String token) {
        return Jwts.parser().verifyWith(getSecretKey()).build().parseSignedClaims(token).getPayload();
    }

    private SecretKey getSecretKey() {
        var keyBytes = Decoders.BASE64.decode(SECRET_KEY);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
