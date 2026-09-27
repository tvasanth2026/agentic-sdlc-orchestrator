package com.vasanth.agenticsdlcorchestrator.workflow.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CompletionEvidence(UUID executionAttemptId, List<UUID> artifactIds,
                                 List<UUID> validationResultIds, boolean toolsSucceeded,
                                 boolean exitGatePassed) {
    public CompletionEvidence {
        Objects.requireNonNull(executionAttemptId);
        artifactIds = List.copyOf(artifactIds);
        validationResultIds = List.copyOf(validationResultIds);
        if (artifactIds.isEmpty()) throw new IllegalArgumentException("completion requires artifacts");
        if (validationResultIds.isEmpty()) throw new IllegalArgumentException("completion requires validation results");
        if (!toolsSucceeded) throw new IllegalArgumentException("completion requires successful tools");
        if (!exitGatePassed) throw new IllegalArgumentException("completion requires a passed exit gate");
    }
}

