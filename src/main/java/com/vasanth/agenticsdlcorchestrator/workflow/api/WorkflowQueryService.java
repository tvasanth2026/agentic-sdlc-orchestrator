package com.vasanth.agenticsdlcorchestrator.workflow.api;

import com.vasanth.agenticsdlcorchestrator.requirement.persistence.ClarificationQuestionRepository;
import com.vasanth.agenticsdlcorchestrator.requirement.persistence.RequirementAnalysisRepository;
import com.vasanth.agenticsdlcorchestrator.requirement.persistence.RequirementItemRepository;
import com.vasanth.agenticsdlcorchestrator.requirement.persistence.RevisionOutputRepository;
import com.vasanth.agenticsdlcorchestrator.workflow.persistence.WorkflowRepository;
import com.vasanth.agenticsdlcorchestrator.workflow.persistence.WorkflowRevisionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class WorkflowQueryService {
    private final WorkflowRepository workflows;
    private final WorkflowRevisionRepository revisions;
    private final RequirementAnalysisRepository analyses;
    private final RequirementItemRepository items;
    private final ClarificationQuestionRepository questions;
    private final RevisionOutputRepository outputs;

    WorkflowQueryService(WorkflowRepository workflows, WorkflowRevisionRepository revisions,
                         RequirementAnalysisRepository analyses, RequirementItemRepository items,
                         ClarificationQuestionRepository questions, RevisionOutputRepository outputs) {
        this.workflows = workflows;
        this.revisions = revisions;
        this.analyses = analyses;
        this.items = items;
        this.questions = questions;
        this.outputs = outputs;
    }

    @Transactional(readOnly = true)
    WorkflowDetailsResponse get(UUID workflowId) {
        var workflow = workflows.findById(workflowId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "workflow not found"));
        var revision = revisions.findByWorkflowIdAndRevisionNumber(workflowId, workflow.getCurrentRevision())
                .orElseThrow();
        var analysis = analyses.findByRevisionId(revision.getId()).orElse(null);
        WorkflowDetailsResponse.AnalysisView view = null;
        if (analysis != null) {
            var itemViews = items.findByAnalysisIdOrderByItemTypeAscItemKeyAsc(analysis.getId()).stream()
                    .map(item -> new WorkflowDetailsResponse.ItemView(item.getItemType().name(), item.getItemKey(),
                            item.getContent(), item.isBehavioral())).toList();
            var questionViews = questions.findByAnalysisIdOrderByQuestionKey(analysis.getId()).stream()
                    .map(question -> new WorkflowDetailsResponse.QuestionView(question.getQuestionKey(),
                            question.getDimension(), question.getPrompt(), question.isResolved())).toList();
            view = new WorkflowDetailsResponse.AnalysisView(analysis.getNormalizedProblem(),
                    analysis.isAmbiguityRequired(), analysis.getRiskLevel(), analysis.isSourceMutationAllowed(),
                    itemViews, questionViews);
        }
        List<WorkflowDetailsResponse.OutputView> outputViews = outputs.findByRevisionIdOrderByOutputKey(revision.getId())
                .stream().map(output -> new WorkflowDetailsResponse.OutputView(output.getOutputKey(),
                        output.getInputDimension().name(), output.getStatus().name(), output.getReusedFromOutputId()))
                .toList();
        return new WorkflowDetailsResponse(workflowId, revision.getId(), revision.getParentRevisionId(),
                revision.getRevisionNumber(), workflow.getStatus(), view, outputViews);
    }
}
