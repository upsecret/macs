package com.macs.authserver.service;

import com.macs.authserver.dto.ValidationRequest;
import com.macs.authserver.dto.ValidationResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
public class AuthValidationService {

    private final AuthTokenService tokenService;

    public AuthValidationService(AuthTokenService tokenService) {
        this.tokenService = tokenService;
    }

    public Mono<ValidationResponse> validateToken(String bearerToken, ValidationRequest request) {
        return Mono.fromCallable(() -> {
            String token = extractToken(bearerToken);

            Claims claims;
            try {
                claims = Jwts.parser()
                        .verifyWith(tokenService.getSigningKey())
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
            } catch (ExpiredJwtException e) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token expired");
            } catch (JwtException e) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
            }

            String appName = claims.get("app_name", String.class);
            String employeeNumber = claims.get("employee_number", String.class);

            @SuppressWarnings("unchecked")
            List<Map<String, String>> permissions =
                    (List<Map<String, String>>) claims.get("permissions", List.class);

            String requiredConnector = request.connector();
            if (requiredConnector != null && !requiredConnector.isBlank()) {
                boolean allowed = permissions != null && permissions.stream()
                        .anyMatch(p -> requiredConnector.equals(p.get("connector")));
                if (!allowed) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Access denied to connector: " + requiredConnector);
                }
            }

            return new ValidationResponse(true, appName, employeeNumber);
        });
    }

    private String extractToken(String bearerToken) {
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
    }
}
