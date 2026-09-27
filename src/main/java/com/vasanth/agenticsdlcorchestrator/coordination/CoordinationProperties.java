package com.vasanth.agenticsdlcorchestrator.coordination;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("agentic.coordination")
public record CoordinationProperties(String workerId, Duration leaseDuration, Duration recoveryInterval) {
    public CoordinationProperties {
        if (workerId == null || workerId.isBlank()) throw new IllegalArgumentException("worker-id is required");
        if (leaseDuration == null || leaseDuration.isNegative() || leaseDuration.isZero())
            throw new IllegalArgumentException("lease-duration must be positive");
        if (recoveryInterval == null || recoveryInterval.isNegative() || recoveryInterval.isZero())
            throw new IllegalArgumentException("recovery-interval must be positive");
    }
}
