package com.vasanth.agenticsdlcorchestrator.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("agentic.patch")
public record PatchPolicyProperties(
        @Min(1) @Max(500) int maxOperations,
        @Min(1024) long maxPatchBytes,
        List<String> permittedRoots) {
    public PatchPolicyProperties { permittedRoots = List.copyOf(permittedRoots); }
}
