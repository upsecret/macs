package com.macs.authserver.controller;

import com.macs.authserver.dto.TokenRequest;
import com.macs.authserver.dto.TokenResponse;
import com.macs.authserver.dto.ValidationRequest;
import com.macs.authserver.dto.ValidationResponse;
import com.macs.authserver.service.AuthTokenService;
import com.macs.authserver.service.AuthValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
            description = "Looks up employee group membership and allowed resources, then generates a JWT")
    public Mono<TokenResponse> issueToken(@RequestBody TokenRequest request) {
        return tokenService.issueToken(request);
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate JWT token",
            description = "Verifies token signature, expiration, and checks resource access")
    public Mono<ValidationResponse> validateToken(
            @RequestHeader("Authorization") String authorization,
            @RequestBody ValidationRequest request) {
        return validationService.validateToken(authorization, request);
    }
}
