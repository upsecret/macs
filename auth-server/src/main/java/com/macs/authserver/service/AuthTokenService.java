package com.macs.authserver.service;

import com.macs.authserver.config.JwtProperties;
import com.macs.authserver.domain.GroupResource;
import com.macs.authserver.domain.UserResource;
import com.macs.authserver.dto.TokenRequest;
import com.macs.authserver.dto.TokenResponse;
import com.macs.authserver.repository.AppInfoRepository;
import com.macs.authserver.repository.GroupInfoRepository;
import com.macs.authserver.repository.GroupResourceRepository;
import com.macs.authserver.repository.UserResourceRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class AuthTokenService {

    private final AppInfoRepository appInfoRepository;
    private final GroupInfoRepository groupInfoRepository;
    private final GroupResourceRepository groupResourceRepository;
    private final UserResourceRepository userResourceRepository;
    private final JwtProperties jwtProperties;

    public AuthTokenService(AppInfoRepository appInfoRepository,
                            GroupInfoRepository groupInfoRepository,
                            GroupResourceRepository groupResourceRepository,
                            UserResourceRepository userResourceRepository,
                            JwtProperties jwtProperties) {
        this.appInfoRepository = appInfoRepository;
        this.groupInfoRepository = groupInfoRepository;
        this.groupResourceRepository = groupResourceRepository;
        this.userResourceRepository = userResourceRepository;
        this.jwtProperties = jwtProperties;
    }

    public Mono<TokenResponse> issueToken(TokenRequest request) {
        return appInfoRepository.findByAppName(request.appName())
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "App not found: " + request.appName())))
                .flatMap(app ->
                        groupInfoRepository.findByAppIdAndEmployeeNumber(
                                        app.getAppId(), request.employeeNumber())
                                .next()
                                .switchIfEmpty(Mono.error(new ResponseStatusException(
                                        HttpStatus.FORBIDDEN, "No group membership for employee: " + request.employeeNumber())))
                                .flatMap(group -> {
                                    Mono<List<String>> groupRes = groupResourceRepository
                                            .findByGroupId(group.getGroupId())
                                            .map(GroupResource::resourceName)
                                            .collectList();

                                    Mono<List<String>> userRes = userResourceRepository
                                            .findByEmployeeNumberAndAppId(
                                                    request.employeeNumber(), app.getAppId())
                                            .map(UserResource::resourceName)
                                            .collectList();

                                    return Mono.zip(groupRes, userRes)
                                            .map(tuple -> {
                                                Set<String> merged = new LinkedHashSet<>(tuple.getT1());
                                                merged.addAll(tuple.getT2());
                                                List<String> allowed = new ArrayList<>(merged);

                                                String token = generateJwt(
                                                        app.getAppName(),
                                                        request.employeeNumber(),
                                                        group.getGroupName(),
                                                        allowed);

                                                return new TokenResponse(
                                                        token,
                                                        app.getAppName(),
                                                        request.employeeNumber(),
                                                        group.getGroupName(),
                                                        allowed);
                                            });
                                })
                );
    }

    private String generateJwt(String appName, String employeeNumber,
                               String group, List<String> allowedResources) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(employeeNumber)
                .claim("app_name", appName)
                .claim("employee_number", employeeNumber)
                .claim("group", group)
                .claim("allowed_resources_list", allowedResources)
                .issuedAt(new Date(now))
                .expiration(new Date(now + jwtProperties.expiration() * 1000L))
                .signWith(getSigningKey())
                .compact();
    }

    SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(
                jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }
}
