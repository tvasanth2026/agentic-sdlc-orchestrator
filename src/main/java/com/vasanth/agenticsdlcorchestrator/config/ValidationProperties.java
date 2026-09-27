package com.vasanth.agenticsdlcorchestrator.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("agentic.validation")
public record ValidationProperties(@NotNull Duration timeout,
                                   @Min(1024) @Max(1048576) int maxOutputCharacters,
                                   @NotNull Duration retryBackoff) {}
