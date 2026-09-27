package com.vasanth.agenticsdlcorchestrator.model;

import java.util.List;
import java.util.Map;

public final class SpecialistJsonSchema {
    private SpecialistJsonSchema() {}

    private static Map<String, Object> stringArray() {
        return Map.of("type", "array", "items", Map.of("type", "string"), "maxItems", 100);
    }

    public static final Map<String, Object> VALUE = Map.of(
            "type", "object",
            "additionalProperties", false,
            "required", List.of("summary", "decisions", "assumptions", "risks", "deliverables", "ready"),
            "properties", Map.of(
                    "summary", Map.of("type", "string"),
                    "decisions", stringArray(),
                    "assumptions", stringArray(),
                    "risks", stringArray(),
                    "deliverables", stringArray(),
                    "ready", Map.of("type", "boolean")));
}
