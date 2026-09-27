package com.vasanth.agenticsdlcorchestrator.workflow.api;

import com.vasanth.agenticsdlcorchestrator.patch.PatchModels.AppliedFileOperation;
import com.vasanth.agenticsdlcorchestrator.workflow.domain.WorkflowStatus;
import java.util.List;
import java.util.UUID;

public record ApplyChangesResponse(UUID workflowId, UUID revisionId, WorkflowStatus status,
                                   List<UUID> proposalIds, String sourceManifestHash,
                                   List<String> changedPaths, String unifiedDiff,
                                   List<AppliedFileOperation> appliedOperations) {
    public ApplyChangesResponse {
        proposalIds = List.copyOf(proposalIds);
        changedPaths = List.copyOf(changedPaths);
        appliedOperations = List.copyOf(appliedOperations);
    }
}
