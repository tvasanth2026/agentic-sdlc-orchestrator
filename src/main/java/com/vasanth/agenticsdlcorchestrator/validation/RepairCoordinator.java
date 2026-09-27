package com.vasanth.agenticsdlcorchestrator.validation;

import com.vasanth.agenticsdlcorchestrator.validation.BuildModels.BuildEvidence;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

public interface RepairCoordinator {
    Optional<UUID> diagnoseProposeValidateAndApply(RepairContext context);

    record RepairContext(UUID workflowId, UUID revisionId, Path workspace, BuildEvidence failure,
                         String baselineManifestHash, int attemptNumber) {}
}
