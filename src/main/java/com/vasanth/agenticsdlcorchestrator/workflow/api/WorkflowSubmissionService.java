package com.vasanth.agenticsdlcorchestrator.workflow.api;

import com.vasanth.agenticsdlcorchestrator.workflow.domain.WorkflowStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class WorkflowSubmissionService {
    WorkflowSubmissionResponse submit(CreateWorkflowRequest request) {
        UUID workflowId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        return new WorkflowSubmissionResponse(workflowId, revisionId, 1, WorkflowStatus.RECEIVED,
                sha256(request.requirement()));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}

