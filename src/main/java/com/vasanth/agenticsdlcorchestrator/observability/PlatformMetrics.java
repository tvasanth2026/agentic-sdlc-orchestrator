package com.vasanth.agenticsdlcorchestrator.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class PlatformMetrics {
    private final MeterRegistry registry;

    public PlatformMetrics(MeterRegistry registry) { this.registry = registry; }

    public void submission() { registry.counter("agentic.workflow.submissions").increment(); }
    public void workflowOutcome(String outcome, Duration duration) {
        registry.counter("agentic.workflow.outcomes", "outcome", outcome).increment();
        registry.timer("agentic.workflow.duration", "outcome", outcome).record(duration);
    }
    public void validation(String outcome) { registry.counter("agentic.validation", "outcome", outcome).increment(); }
    public void recovery(String decision, Duration duration) {
        registry.counter("agentic.recovery.decisions", "decision", decision).increment();
        if ("REPAIR".equals(decision)) registry.timer("agentic.repair.duration").record(duration);
    }
    public Timer.Sample modelStarted() { return Timer.start(registry); }
    public void modelFinished(Timer.Sample sample, String provider, String outcome) {
        registry.counter("agentic.model.calls", "provider", provider, "outcome", outcome).increment();
        sample.stop(registry.timer("agentic.model.latency", "provider", provider, "outcome", outcome));
    }
}
