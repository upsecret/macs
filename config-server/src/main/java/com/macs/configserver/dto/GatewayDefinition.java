package com.macs.configserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.LinkedHashMap;
import java.util.Map;

@Schema(description = "Gateway predicate or filter definition (name + args)")
public record GatewayDefinition(
        @Schema(description = "Filter/Predicate name", example = "RewritePath")
        String name,
        @Schema(description = "Arguments map", example = "{\"regexp\":\"/api/(?<seg>.*)\",\"replacement\":\"/${seg}\"}")
        Map<String, String> args
) {
    public GatewayDefinition {
        if (args == null) args = new LinkedHashMap<>();
    }

    /** "StripPrefix=1" 같은 shorthand 문자열에서 생성 */
    public static GatewayDefinition fromShorthand(String shorthand) {
        int eq = shorthand.indexOf('=');
        if (eq < 0) return new GatewayDefinition(shorthand, Map.of());
        String name = shorthand.substring(0, eq);
        String value = shorthand.substring(eq + 1);
        // 쉼표로 분리되는 multi-arg shorthand: "RewritePath=/old, /new"
        String[] parts = value.split(",\\s*");
        Map<String, String> args = new LinkedHashMap<>();
        for (int i = 0; i < parts.length; i++) {
            args.put("_genkey_" + i, parts[i]);
        }
        return new GatewayDefinition(name, args);
    }

    /** shorthand 가능하면 shorthand 문자열로, 아니면 null */
    public String toShorthand() {
        if (args.isEmpty()) return name;
        boolean allGenkey = args.keySet().stream().allMatch(k -> k.startsWith("_genkey_"));
        if (!allGenkey) return null;
        String joined = args.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        return name + "=" + joined;
    }
}
