package com.macs.authserver.service;

import com.macs.authserver.dto.PermissionEntry;
import com.macs.authserver.dto.ValidationRequest;
import com.macs.authserver.dto.ValidationResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class AuthValidationService {

    private static final Logger log = LoggerFactory.getLogger(AuthValidationService.class);

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
                        log.warn("Token validation failed: missing employee_number in claims");
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED, "Token missing employee_number"));
                    }

                    String connector = request.connector();
                    if (connector == null || connector.isBlank()) {
                        log.info("Token valid (signature+expiry only) emp={}", employeeNumber);
                        return Mono.just(new ValidationResponse(true, true, employeeNumber));
                    }

                    String appName = request.appName();
                    if (appName == null || appName.isBlank()) {
                        log.warn("Validate rejected: connector={} provided without app_name (emp={})",
                                connector, employeeNumber);
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "app_name is required when connector is provided"));
                    }

                    return adminPermissionClient.fetch(appName, employeeNumber)
                            .map(perms -> {
                                boolean allowed = matchesConnector(perms, connector);
                                if (allowed) {
                                    log.info("Validate ALLOW app={} emp={} connector={} (grants={})",
                                            appName, employeeNumber, connector, perms.size());
                                } else {
                                    log.warn("Validate DENY app={} emp={} connector={} (grants={})",
                                            appName, employeeNumber, connector, perms.size());
                                }
                                return new ValidationResponse(true, allowed, employeeNumber);
                            });
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
            log.warn("Token validation failed: expired");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token expired");
        } catch (JwtException e) {
            log.warn("Token validation failed: invalid signature/format ({})", e.getClass().getSimpleName());
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
