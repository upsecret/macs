package com.macs.authserver.controller;

import com.macs.authserver.dto.TokenRequest;
import com.macs.authserver.dto.TokenResponse;
import com.macs.authserver.dto.ValidationRequest;
import com.macs.authserver.dto.ValidationResponse;
import com.macs.authserver.service.AuthTokenService;
import com.macs.authserver.service.AuthValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Token issuance and validation")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthTokenService tokenService;
    private final AuthValidationService validationService;

    public AuthController(AuthTokenService tokenService,
                          AuthValidationService validationService) {
        this.tokenService = tokenService;
        this.validationService = validationService;
    }

    @PostMapping("/token")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Issue JWT token",
            description = "Issues a JWT with client_app and employee_number claims. " +
                    "Per-request permission checks happen via /validate. " +
                    "Requests require Client-App and Employee-Number headers on the gateway-facing side.")
    public Mono<TokenResponse> issueToken(@RequestBody TokenRequest request) {
        log.info("Token request client_app={} employee_number={}", request.clientApp(), request.employeeNumber());
        return tokenService.issueToken(request);
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate JWT token and check connector access",
            description = "Verifies token signature/expiration, asserts the request's client_app and " +
                    "employee_number match the token claims, and checks PERMISSION for " +
                    "(client_app, employee_number, connector). connector is required.")
    public Mono<ValidationResponse> validateToken(
            @RequestHeader("Authorization") String authorization,
            @RequestBody ValidationRequest request) {
        log.info("Validation request client_app={} employee_number={} connector={}",
                request.clientApp(), request.employeeNumber(), request.connector());
        return validationService.validateToken(authorization, request);
    }
}
