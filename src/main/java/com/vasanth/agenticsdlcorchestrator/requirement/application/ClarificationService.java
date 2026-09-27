package com.vasanth.agenticsdlcorchestrator.requirement.application;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.vasanth.agenticsdlcorchestrator.requirement.persistence.ClarificationEntity;
import com.vasanth.agenticsdlcorchestrator.requirement.persistence.ClarificationQuestionRepository;
import com.vasanth.agenticsdlcorchestrator.requirement.persistence.ClarificationRepository;
import com.vasanth.agenticsdlcorchestrator.requirement.persistence.RequirementAnalysisRepository;
import com.vasanth.agenticsdlcorchestrator.requirement.persistence.RevisionOutputEntity;
import com.vasanth.agenticsdlcorchestrator.requirement.persistence.RevisionOutputEntity.InputDimension;
import com.vasanth.agenticsdlcorchestrator.requirement.persistence.RevisionOutputEntity.OutputStatus;
import com.vasanth.agenticsdlcorchestrator.requirement.persistence.RevisionOutputRepository;
import com.vasanth.agenticsdlcorchestrator.workflow.api.ClarificationRequest;
import com.vasanth.agenticsdlcorchestrator.workflow.api.ClarificationResponse;
import com.vasanth.agenticsdlcorchestrator.workflow.domain.WorkflowStatus;
import com.vasanth.agenticsdlcorchestrator.workflow.persistence.WorkflowRepository;
import com.vasanth.agenticsdlcorchestrator.workflow.persistence.WorkflowRevisionEntity;
import com.vasanth.agenticsdlcorchestrator.workflow.persistence.WorkflowRevisionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClarificationService {
    private final ClarificationAuthenticator authenticator;
    private final WorkflowRepository workflows;
    private final WorkflowRevisionRepository revisions;
    private final RequirementAnalysisRepository analyses;
    private final ClarificationQuestionRepository questions;
    private final ClarificationRepository clarifications;
    private final RevisionOutputRepository outputs;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;

    public ClarificationService(ClarificationAuthenticator authenticator, WorkflowRepository workflows,
                                WorkflowRevisionRepository revisions, RequirementAnalysisRepository analyses,
                                ClarificationQuestionRepository questions, ClarificationRepository clarifications,
                                RevisionOutputRepository outputs, ApplicationEventPublisher events,
                                ObjectMapper objectMapper) {
        this.authenticator = authenticator;
        this.workflows = workflows;
        this.revisions = revisions;
        this.analyses = analyses;
        this.questions = questions;
        this.clarifications = clarifications;
        this.outputs = outputs;
        this.events = events;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ClarificationResponse clarify(UUID workflowId, ClarificationRequest request,
                                         String operatorToken, String operatorId) {
        authenticator.authenticate(operatorToken);
        if (operatorId == null || operatorId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "operator identity is required");
        }
        var workflow = workflows.findById(workflowId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "workflow not found"));
        if (workflow.getStatus() != WorkflowStatus.AWAITING_CLARIFICATION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "workflow is not awaiting clarification");
        }
        var previous = revisions.findByWorkflowIdAndRevisionNumber(workflowId, workflow.getCurrentRevision())
                .orElseThrow();
        var analysis = analyses.findByRevisionId(previous.getId()).orElseThrow();
        var unresolved = questions.findByAnalysisIdOrderByQuestionKey(analysis.getId());
        validateAnswers(unresolved.stream().map(question -> question.getQuestionKey()).collect(Collectors.toSet()),
                request.answers());
        unresolved.forEach(question -> question.resolve());
        questions.saveAll(unresolved);

        Instant now = Instant.now();
        UUID newRevisionId = UUID.randomUUID();
        int revisionNumber = previous.getRevisionNumber() + 1;
        revisions.save(new WorkflowRevisionEntity(newRevisionId, workflowId, revisionNumber, previous.getId(),
                sha256(request.clarifiedRequirement()), now));
        workflow.advanceTo(revisionNumber, now);
        workflows.save(workflow);
        clarifications.save(new ClarificationEntity(UUID.randomUUID(), workflowId, previous.getId(), newRevisionId,
                operatorId, serialize(request.answers()), request.clarifiedRequirement(), now));
        reviseOutputs(previous.getId(), newRevisionId);
        events.publishEvent(new RequirementSubmittedEvent(workflowId, newRevisionId,
                request.clarifiedRequirement(), workflow.getRepositoryPath()));
        return new ClarificationResponse(workflowId, newRevisionId, previous.getId(), revisionNumber,
                WorkflowStatus.RECEIVED);
    }

    private void validateAnswers(Set<String> required, Map<String, String> supplied) {
        if (!supplied.keySet().containsAll(required)) {
            Set<String> missing = required.stream().filter(key -> !supplied.containsKey(key)).collect(Collectors.toSet());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing clarification answers: " + missing);
        }
    }

    private void reviseOutputs(UUID previousRevisionId, UUID newRevisionId) {
        var previousOutputs = outputs.findByRevisionIdOrderByOutputKey(previousRevisionId);
        for (RevisionOutputEntity previous : previousOutputs) {
            if (previous.getInputDimension() == InputDimension.REQUIREMENT) {
                previous.invalidate();
                outputs.save(new RevisionOutputEntity(UUID.randomUUID(), newRevisionId, previous.getOutputKey(),
                        InputDimension.REQUIREMENT, OutputStatus.PENDING, null));
            } else {
                outputs.save(new RevisionOutputEntity(UUID.randomUUID(), newRevisionId, previous.getOutputKey(),
                        InputDimension.REPOSITORY, OutputStatus.REUSED, previous.getId()));
            }
        }
        outputs.saveAll(previousOutputs);
    }

    private String serialize(Map<String, String> answers) {
        try {
            return objectMapper.writeValueAsString(answers);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("clarification answers cannot be serialized", exception);
        }
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
