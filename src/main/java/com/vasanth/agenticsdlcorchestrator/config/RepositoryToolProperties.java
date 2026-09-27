package com.vasanth.agenticsdlcorchestrator.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("agentic.repository")
public record RepositoryToolProperties(
        @NotEmpty List<Path> approvedRoots,
        @Min(1) @Max(20_000) int maxFiles,
        @Min(1) long maxFileBytes,
        @Min(1) long maxTotalBytes,
        @Min(1) @Max(10_000) int maxSearchMatches) {
    public RepositoryToolProperties {
        approvedRoots = List.copyOf(approvedRoots);
    }
}

