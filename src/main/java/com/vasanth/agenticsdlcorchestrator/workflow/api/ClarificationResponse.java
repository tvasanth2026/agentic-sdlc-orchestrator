package com.vasanth.agenticsdlcorchestrator.workflow.api;

import com.vasanth.agenticsdlcorchestrator.workflow.domain.WorkflowStatus;
import java.util.UUID;

public record ClarificationResponse(UUID workflowId, UUID revisionId, UUID parentRevisionId,
                                    int revision, WorkflowStatus status) {
}

