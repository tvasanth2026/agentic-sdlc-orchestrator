package com.vasanth.agenticsdlcorchestrator.workflow.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WorkflowRevision(UUID id, UUID workflowId, int number, UUID parentRevisionId,
                               String requirementHash, WorkflowStatus state, Instant createdAt) {
    public WorkflowRevision {
        Objects.requireNonNull(id);
        Objects.requireNonNull(workflowId);
        Objects.requireNonNull(requirementHash);
        Objects.requireNonNull(state);
        Objects.requireNonNull(createdAt);
        if (number < 1) throw new IllegalArgumentException("revision number must be positive");
        if (requirementHash.length() != 64) throw new IllegalArgumentException("requirementHash must be SHA-256");
    }
}

