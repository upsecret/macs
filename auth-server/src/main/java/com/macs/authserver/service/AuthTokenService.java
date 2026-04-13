package com.macs.authserver.service;

import com.macs.authserver.config.JwtProperties;
import com.macs.authserver.dto.TokenRequest;
import com.macs.authserver.dto.TokenResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class AuthTokenService {

    private final JwtProperties jwtProperties;

    public AuthTokenService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public Mono<TokenResponse> issueToken(TokenRequest request) {
        String empNo = request.employeeNumber();
        String token = generateJwt(empNo);
        return Mono.just(new TokenResponse(token, empNo));
    }

    private String generateJwt(String empNo) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(empNo)
                .claim("employee_number", empNo)
                .issuedAt(new Date(now))
                .expiration(new Date(now + jwtProperties.expiration() * 1000L))
                .signWith(getSigningKey())
                .compact();
    }

    SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }
}
