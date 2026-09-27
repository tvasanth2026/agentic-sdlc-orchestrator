package com.vasanth.agenticsdlcorchestrator.model;

import java.util.List;
import java.util.Map;

public final class PatchJsonSchema {
    private PatchJsonSchema() {}

    public static final Map<String, Object> VALUE = Map.of(
            "type", "object",
            "additionalProperties", false,
            "required", List.of("operations"),
            "properties", Map.of("operations", Map.of(
                    "type", "array", "minItems", 1, "maxItems", 100,
                    "items", Map.of(
                            "type", "object", "additionalProperties", false,
                            "required", List.of("relativePath", "operationType", "content", "expectedSha256",
                                    "reason", "requirementId", "acceptanceCriterionIds", "taskId",
                                    "inputArtifactHashes"),
                            "properties", Map.ofEntries(
                                    Map.entry("relativePath", Map.of("type", "string")),
                                    Map.entry("operationType", Map.of("type", "string", "enum", List.of("CREATE", "UPDATE", "DELETE"))),
                                    Map.entry("content", Map.of("type", List.of("string", "null"))),
                                    Map.entry("expectedSha256", Map.of("type", List.of("string", "null"))),
                                    Map.entry("reason", Map.of("type", "string")),
                                    Map.entry("requirementId", Map.of("type", "string")),
                                    Map.entry("acceptanceCriterionIds", Map.of("type", "array", "items", Map.of("type", "string"))),
                                    Map.entry("taskId", Map.of("type", "string")),
                                    Map.entry("inputArtifactHashes", Map.of("type", "array", "items", Map.of("type", "string"))))))));
}
