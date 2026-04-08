package com.macs.authserver.domain;

public record SystemConnector(
        String systemName,
        String connectorName,
        String description
) {
}
