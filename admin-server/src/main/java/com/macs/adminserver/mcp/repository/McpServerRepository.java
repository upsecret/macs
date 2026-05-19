package com.macs.adminserver.mcp.repository;

import com.macs.adminserver.mcp.domain.McpServer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface McpServerRepository extends JpaRepository<McpServer, String> {
}
