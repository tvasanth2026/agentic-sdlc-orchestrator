package com.vasanth.agenticsdlcorchestrator.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("agentic.governance")
public record GovernanceProperties(@NotBlank String operatorToken,
                                   @NotBlank String changeApproverToken,
                                   @NotBlank String releaseApproverToken) {}
