package com.macs.authserver.repository;

import com.macs.authserver.domain.GroupInfo;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface GroupInfoRepository extends ReactiveCrudRepository<GroupInfo, String> {

    Flux<GroupInfo> findByAppId(String appId);

    @Query("""
            SELECT gi.GROUP_ID, gi.APP_ID, gi.GROUP_NAME
            FROM GROUP_INFO gi
            JOIN GROUP_MEMBER gm ON gi.GROUP_ID = gm.GROUP_ID
            WHERE gi.APP_ID = :appId AND gm.EMPLOYEE_NUMBER = :employeeNumber
            """)
    Flux<GroupInfo> findByAppIdAndEmployeeNumber(String appId, String employeeNumber);
}
