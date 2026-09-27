package com.vasanth.agenticsdlcorchestrator.governance;

import java.time.Instant;
import java.util.UUID;

public final class GovernanceModels {
    private GovernanceModels() {}

    public record Approval(UUID id, UUID revisionId, String gate, String evidenceHash,
                           String approver, Decision decision, Instant createdAt) {}
    public record PolicyDecision(UUID id, UUID revisionId, UUID taskId, String policyKey,
                                 Decision decision, String reason, Instant createdAt) {}
    public record AuditEvent(UUID id, UUID workflowId, UUID revisionId, UUID taskId,
                             String eventType, String actor, String details, Instant occurredAt) {}
    public enum Decision { APPROVED, REJECTED }
}

