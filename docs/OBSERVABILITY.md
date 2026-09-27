# Observability

Prometheus is exposed at `/actuator/prometheus` to authenticated operators. Metrics use the `agentic_*` Prometheus naming convention and cover submissions, workflow outcomes/duration, validation, recovery decisions, repair duration, model calls/latency, expired-lease recovery, and lease takeover. Audit, attempt, approval, policy, artifact, claim, and outcome rows provide durable drill-down evidence.

Exact PromQL indicators:

```promql
# success rate
sum(rate(agentic_workflow_outcomes_total{outcome="RELEASE_READY"}[5m])) / clamp_min(sum(rate(agentic_workflow_outcomes_total[5m])), 1)
# retry frequency
sum(rate(agentic_recovery_decisions_total{decision="RETRY"}[5m])) / clamp_min(sum(rate(agentic_validation_total[5m])), 1)
# rollback frequency
sum(rate(agentic_workflow_outcomes_total{outcome="ROLLED_BACK"}[5m])) / clamp_min(sum(rate(agentic_workflow_outcomes_total[5m])), 1)
# mean time to repair
rate(agentic_repair_duration_seconds_sum[15m]) / clamp_min(rate(agentic_repair_duration_seconds_count[15m]), 1)
# repair success rate
sum(rate(agentic_recovery_decisions_total{decision="REPAIR"}[5m])) / clamp_min(sum(rate(agentic_recovery_decisions_total[5m])), 1)
# validation failure rate
sum(rate(agentic_validation_total{outcome="failure"}[5m])) / clamp_min(sum(rate(agentic_validation_total[5m])), 1)
# model failure rate
sum(rate(agentic_model_calls_total{outcome="failure"}[5m])) / clamp_min(sum(rate(agentic_model_calls_total[5m])), 1)
# p95 end-to-end latency
histogram_quantile(0.95, sum by (le) (rate(agentic_workflow_duration_seconds_bucket[5m])))
```

Raw counters are telemetry, not computed indicators; the queries above perform the required rate calculations.
