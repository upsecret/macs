package com.macs.adminserver.connector.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Connector create/update payload")
public record ConnectorRequest(
        String id,
        String title,
        String description,
        String type,
        @Schema(description = "선택: 외부 OpenAPI JSON endpoint 절대 URL. 비우면 gateway /v3/api-docs/{id} 사용",
                example = "http://10.40.59.61:8080/token-dic/v3/api-docs")
        String docsUrl
) {
}
