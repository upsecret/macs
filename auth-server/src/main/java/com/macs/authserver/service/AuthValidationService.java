package com.macs.authserver.service;

import com.macs.authserver.dto.PermissionEntry;
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

@Service
public class AuthValidationService {

    private final AuthTokenService tokenService;
    private final AdminPermissionClient adminPermissionClient;

    public AuthValidationService(AuthTokenService tokenService,
                                 AdminPermissionClient adminPermissionClient) {
        this.tokenService = tokenService;
        this.adminPermissionClient = adminPermissionClient;
    }

    public Mono<ValidationResponse> validateToken(String bearerToken, ValidationRequest request) {
        return Mono.fromCallable(() -> parseClaims(bearerToken))
                .flatMap(claims -> {
                    String employeeNumber = claims.get("employee_number", String.class);
                    if (employeeNumber == null || employeeNumber.isBlank()) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED, "Token missing employee_number"));
                    }

                    String connector = request.connector();
                    if (connector == null || connector.isBlank()) {
                        return Mono.just(new ValidationResponse(true, true, employeeNumber));
                    }

                    String appName = request.appName();
                    if (appName == null || appName.isBlank()) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "app_name is required when connector is provided"));
                    }

                    return adminPermissionClient.fetch(appName, employeeNumber)
                            .map(perms -> new ValidationResponse(
                                    true,
                                    matchesConnector(perms, connector),
                                    employeeNumber));
                });
    }

    private Claims parseClaims(String bearerToken) {
        String token = extractToken(bearerToken);
        try {
            return Jwts.parser()
                    .verifyWith(tokenService.getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token expired");
        } catch (JwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }
    }

    private boolean matchesConnector(List<PermissionEntry> permissions, String connector) {
        return permissions != null && permissions.stream()
                .anyMatch(p -> connector.equals(p.connector()));
    }

    private String extractToken(String bearerToken) {
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
    }
}
