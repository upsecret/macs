package com.macs.authserver.service;

import com.macs.authserver.dto.ValidationRequest;
import com.macs.authserver.dto.ValidationResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class AuthValidationService {

    private final AuthTokenService tokenService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

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
            String group = claims.get("group", String.class);

            @SuppressWarnings("unchecked")
            List<String> allowedResources = claims.get("allowed_resources_list", List.class);

            if (request.requestApp() != null && !request.requestApp().isBlank()) {
                boolean hasAccess = allowedResources.stream()
                        .anyMatch(pattern -> pathMatcher.match(pattern, request.requestApp()));
                if (!hasAccess) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Access denied to resource: " + request.requestApp());
                }
            }

            return new ValidationResponse(true, appName, employeeNumber, group);
        });
    }

    private String extractToken(String bearerToken) {
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
    }
}
