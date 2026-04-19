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
        String requestClientApp = request.clientApp();
        String requestEmployeeNumber = request.employeeNumber();
        String connector = request.connector();

        if (requestClientApp == null || requestClientApp.isBlank()) {
            log.warn("Validate rejected: missing client_app in request body");
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "client_app is required"));
        }
        if (requestEmployeeNumber == null || requestEmployeeNumber.isBlank()) {
            log.warn("Validate rejected: missing employee_number in request body (client_app={})", requestClientApp);
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "employee_number is required"));
        }
        if (connector == null || connector.isBlank()) {
            log.warn("Validate rejected: missing connector (client_app={} emp={})",
                    requestClientApp, requestEmployeeNumber);
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "connector is required"));
        }

        return Mono.fromCallable(() -> parseClaims(bearerToken))
                .flatMap(claims -> {
                    String tokenClientApp = claims.get("client_app", String.class);
                    String tokenEmployeeNumber = claims.get("employee_number", String.class);

                    if (tokenClientApp == null || tokenClientApp.isBlank()) {
                        log.warn("Token validation failed: missing client_app in claims");
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED, "Token missing client_app"));
                    }
                    if (tokenEmployeeNumber == null || tokenEmployeeNumber.isBlank()) {
                        log.warn("Token validation failed: missing employee_number in claims");
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED, "Token missing employee_number"));
                    }
                    if (!tokenClientApp.equals(requestClientApp)) {
                        log.warn("Token validation failed: client_app mismatch token={} request={}",
                                tokenClientApp, requestClientApp);
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED, "client_app does not match token"));
                    }
                    if (!tokenEmployeeNumber.equals(requestEmployeeNumber)) {
                        log.warn("Token validation failed: employee_number mismatch token={} request={} (client_app={})",
                                tokenEmployeeNumber, requestEmployeeNumber, tokenClientApp);
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED, "employee_number does not match token"));
                    }

                    return adminPermissionClient.fetch(tokenClientApp, tokenEmployeeNumber)
                            .map(perms -> {
                                boolean allowed = matchesConnector(perms, connector);
                                if (allowed) {
                                    log.info("Validate ALLOW client_app={} emp={} connector={} (grants={})",
                                            tokenClientApp, tokenEmployeeNumber, connector, perms.size());
                                } else {
                                    log.warn("Validate DENY client_app={} emp={} connector={} (grants={})",
                                            tokenClientApp, tokenEmployeeNumber, connector, perms.size());
                                }
                                return new ValidationResponse(true, allowed, tokenEmployeeNumber);
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
