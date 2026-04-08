package com.macs.configserver.repository;

import com.macs.configserver.entity.ConfigProperty;
import com.macs.configserver.entity.ConfigPropertyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ConfigPropertyRepository extends JpaRepository<ConfigProperty, ConfigPropertyId> {

    List<ConfigProperty> findByIdApplicationAndIdProfileAndIdLabel(
            String application, String profile, String label);

    @Query("""
            SELECT p FROM ConfigProperty p
            WHERE p.id.application = :app
              AND p.id.profile = :profile
              AND p.id.label = :label
              AND p.id.propKey LIKE :keyPattern
            """)
    List<ConfigProperty> findByKeyPattern(
            @Param("app") String application,
            @Param("profile") String profile,
            @Param("label") String label,
            @Param("keyPattern") String keyPattern);

    @Modifying(clearAutomatically = true)
    @Query("""
            DELETE FROM ConfigProperty p
            WHERE p.id.application = :app
              AND p.id.profile = :profile
              AND p.id.label = :label
              AND p.id.propKey LIKE :keyPattern
            """)
    void deleteByKeyPattern(
            @Param("app") String application,
            @Param("profile") String profile,
            @Param("label") String label,
            @Param("keyPattern") String keyPattern);
}
