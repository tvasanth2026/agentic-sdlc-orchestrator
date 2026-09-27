package com.vasanth.agenticsdlcorchestrator.workflow.api;

import com.vasanth.agenticsdlcorchestrator.requirement.domain.RequirementAnalysis.RiskLevel;
import com.vasanth.agenticsdlcorchestrator.workflow.domain.WorkflowStatus;
import java.util.List;
import java.util.UUID;

public record WorkflowDetailsResponse(
        UUID workflowId,
        UUID revisionId,
        UUID parentRevisionId,
        int revision,
        WorkflowStatus status,
        AnalysisView analysis,
        List<OutputView> outputs) {

    public record AnalysisView(String normalizedProblem, boolean clarificationRequired, RiskLevel riskLevel,
                               boolean sourceMutationAllowed, List<ItemView> items,
                               List<QuestionView> questions) {}
    public record ItemView(String type, String key, String content, boolean behavioral) {}
    public record QuestionView(String key, String dimension, String prompt, boolean resolved) {}
    public record OutputView(String key, String inputDimension, String status, UUID reusedFromOutputId) {}
}

