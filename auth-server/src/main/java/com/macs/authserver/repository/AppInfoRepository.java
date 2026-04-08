package com.macs.authserver.repository;

import com.macs.authserver.domain.AppInfo;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface AppInfoRepository extends ReactiveCrudRepository<AppInfo, String> {

    Mono<AppInfo> findByAppName(String appName);
}
