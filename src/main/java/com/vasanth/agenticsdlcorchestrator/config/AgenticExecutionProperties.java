package com.vasanth.agenticsdlcorchestrator.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("agentic.execution")
public record AgenticExecutionProperties(
        boolean deterministic,
        @Min(1) @Max(10) int maxAttempts,
        @NotNull Path workspaceRoot) {
}

