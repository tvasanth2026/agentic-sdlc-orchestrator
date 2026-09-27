package com.vasanth.agenticsdlcorchestrator.workflow.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Workflow(
        UUID id,
        String originalRequirement,
        String repositoryPath,
        WorkflowStatus status,
        int currentRevision,
        Instant createdAt,
        Instant updatedAt) {

    public Workflow {
        Objects.requireNonNull(id);
        Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
        if (originalRequirement == null || originalRequirement.isBlank()) {
            throw new IllegalArgumentException("originalRequirement is required");
        }
        if (repositoryPath == null || repositoryPath.isBlank()) {
            throw new IllegalArgumentException("repositoryPath is required");
        }
        if (currentRevision < 1) {
            throw new IllegalArgumentException("currentRevision must be positive");
        }
    }
}

