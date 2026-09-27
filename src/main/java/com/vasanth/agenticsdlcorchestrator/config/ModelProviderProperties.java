package com.vasanth.agenticsdlcorchestrator.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("agentic.model")
public record ModelProviderProperties(
        @NotBlank String provider,
        @NotBlank String model,
        @NotNull URI baseUrl,
        String apiKey,
        @NotNull Duration timeout,
        @Min(1024) @Max(1_000_000) int maxContextCharacters,
        @Min(512) @Max(100_000) int maxOutputCharacters) {
}
