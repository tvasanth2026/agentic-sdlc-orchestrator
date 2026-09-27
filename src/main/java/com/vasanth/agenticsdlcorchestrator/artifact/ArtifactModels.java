package com.vasanth.agenticsdlcorchestrator.artifact;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ArtifactModels {
    private ArtifactModels() {}

    public record EngineeringArtifact(UUID id, UUID workflowId, UUID revisionId, UUID producingTaskId,
                                      String type, String key, String schemaVersion, String storageLocation,
                                      String sha256, List<String> inputHashes, ValidationStatus validationStatus,
                                      Instant createdAt) {}
    public record ValidationResult(UUID id, UUID artifactId, String validator, ValidationStatus status,
                                   String summary, String evidenceLocation, Instant createdAt) {}
    public enum ValidationStatus { PENDING, PASSED, FAILED }
}

