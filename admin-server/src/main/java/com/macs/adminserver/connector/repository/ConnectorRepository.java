package com.macs.adminserver.connector.repository;

import com.macs.adminserver.connector.domain.Connector;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConnectorRepository extends JpaRepository<Connector, String> {
}
