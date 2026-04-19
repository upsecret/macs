package com.macs.authserver.service;

import com.macs.authserver.config.JwtProperties;
import com.macs.authserver.dto.TokenRequest;
import com.macs.authserver.dto.TokenResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class AuthTokenService {

    private static final Logger log = LoggerFactory.getLogger(AuthTokenService.class);

    private final JwtProperties jwtProperties;

    public AuthTokenService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public Mono<TokenResponse> issueToken(TokenRequest request) {
        String clientApp = request.clientApp();
        String empNo = request.employeeNumber();
        if (clientApp == null || clientApp.isBlank()) {
            log.warn("Token issuance rejected: empty client_app");
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "client_app required"));
        }
        if (empNo == null || empNo.isBlank()) {
            log.warn("Token issuance rejected: empty employee_number (client_app={})", clientApp);
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "employee_number required"));
        }
        String token = generateJwt(clientApp, empNo);
        log.info("Token issued client_app={} employee_number={} expires_in={}s",
                clientApp, empNo, jwtProperties.expiration());
        return Mono.just(new TokenResponse(token, clientApp, empNo));
    }

    private String generateJwt(String clientApp, String empNo) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(empNo)
                .claim("client_app", clientApp)
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
