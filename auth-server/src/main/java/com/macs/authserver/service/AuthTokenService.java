package com.macs.authserver.service;

import com.macs.authserver.config.JwtProperties;
import com.macs.authserver.dto.PermissionEntry;
import com.macs.authserver.dto.TokenRequest;
import com.macs.authserver.dto.TokenResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class AuthTokenService {

    private final AdminPermissionClient adminPermissionClient;
    private final JwtProperties jwtProperties;

    public AuthTokenService(AdminPermissionClient adminPermissionClient,
                            JwtProperties jwtProperties) {
        this.adminPermissionClient = adminPermissionClient;
        this.jwtProperties = jwtProperties;
    }

    public Mono<TokenResponse> issueToken(TokenRequest request) {
        String appName = request.appName();
        String empNo = request.employeeNumber();

        return adminPermissionClient.fetch(appName, empNo)
                .flatMap(permissions -> {
                    if (permissions.isEmpty()) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "No permissions for " + empNo + " in " + appName));
                    }
                    String token = generateJwt(appName, empNo, permissions);
                    return Mono.just(new TokenResponse(token, appName, empNo, permissions));
                });
    }

    private String generateJwt(String appName, String empNo, List<PermissionEntry> permissions) {
        long now = System.currentTimeMillis();
        List<Map<String, String>> permissionsClaim = permissions.stream()
                .map(p -> Map.of(
                        "system", p.system(),
                        "connector", p.connector(),
                        "role", p.role()))
                .toList();

        return Jwts.builder()
                .subject(empNo)
                .claim("app_name", appName)
                .claim("employee_number", empNo)
                .claim("permissions", permissionsClaim)
                .issuedAt(new Date(now))
                .expiration(new Date(now + jwtProperties.expiration() * 1000L))
                .signWith(getSigningKey())
                .compact();
    }

    SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }
}
