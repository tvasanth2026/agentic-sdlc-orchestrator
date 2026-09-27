package com.vasanth.agenticsdlcorchestrator.validation;

import com.vasanth.agenticsdlcorchestrator.config.AgenticExecutionProperties;
import com.vasanth.agenticsdlcorchestrator.config.RepositoryToolProperties;
import com.vasanth.agenticsdlcorchestrator.config.ValidationProperties;
import com.vasanth.agenticsdlcorchestrator.planning.persistence.RepositoryAnalysisRepository;
import com.vasanth.agenticsdlcorchestrator.repository.workspace.RepositoryWorkspaceService;
import com.vasanth.agenticsdlcorchestrator.validation.BuildModels.BuildEvidence;
import com.vasanth.agenticsdlcorchestrator.validation.BuildModels.FailureClassification;
import com.vasanth.agenticsdlcorchestrator.validation.BuildModels.MavenCapability;
import com.vasanth.agenticsdlcorchestrator.validation.BuildModels.RecoveryDecision;
import com.vasanth.agenticsdlcorchestrator.validation.BuildModels.ValidationAttemptEvidence;
import com.vasanth.agenticsdlcorchestrator.validation.BuildModels.ValidationOutcome;
import com.vasanth.agenticsdlcorchestrator.workflow.domain.WorkflowStatus;
import com.vasanth.agenticsdlcorchestrator.workflow.persistence.WorkflowRepository;
import com.vasanth.agenticsdlcorchestrator.workflow.persistence.WorkflowRevisionRepository;
import com.vasanth.agenticsdlcorchestrator.governance.GovernanceService;
import com.vasanth.agenticsdlcorchestrator.observability.PlatformMetrics;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkflowValidationService {
    private final WorkflowRepository workflows;
    private final WorkflowRevisionRepository revisions;
    private final RepositoryAnalysisRepository analyses;
    private final FixedMavenCapabilityTool tool;
    private final RepairCoordinator repairs;
    private final AgenticExecutionProperties execution;
    private final RepositoryToolProperties repositoryProperties;
    private final ValidationProperties validationProperties;
    private final JdbcTemplate jdbc;
    private final GovernanceService governance;
    private final PlatformMetrics metrics;

    public WorkflowValidationService(WorkflowRepository workflows, WorkflowRevisionRepository revisions,
                                     RepositoryAnalysisRepository analyses, FixedMavenCapabilityTool tool,
                                     RepairCoordinator repairs, AgenticExecutionProperties execution,
                                     RepositoryToolProperties repositoryProperties,
                                     ValidationProperties validationProperties, JdbcTemplate jdbc,
                                     GovernanceService governance, PlatformMetrics metrics) {
        this.workflows = workflows;
        this.revisions = revisions;
        this.analyses = analyses;
        this.tool = tool;
        this.repairs = repairs;
        this.execution = execution;
        this.repositoryProperties = repositoryProperties;
        this.validationProperties = validationProperties;
        this.jdbc = jdbc;
        this.governance = governance;
        this.metrics = metrics;
    }

    public ValidationOutcome validate(UUID workflowId) {
        var workflow = workflows.findById(workflowId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "workflow not found"));
        if (workflow.getStatus() != WorkflowStatus.EXECUTING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "workflow is not ready for validation");
        }
        var revision = revisions.findByWorkflowIdAndRevisionNumber(workflowId, workflow.getCurrentRevision()).orElseThrow();
        var analysis = analyses.findByRevisionId(revision.getId()).orElseThrow();
        Path workspace = Path.of(analysis.getWorkspaceLocation());
        Path baseline = workspace.getParent().resolve("snapshots").resolve("baseline");
        RepositoryWorkspaceService workspaces = new RepositoryWorkspaceService(execution.workspaceRoot(), repositoryProperties);
        UUID taskId = validationTask(revision.getId());
        workflow.transition(WorkflowStatus.VALIDATING, Instant.now());
        revision.transition(WorkflowStatus.VALIDATING);
        workflows.save(workflow);
        revisions.save(revision);

        List<ValidationAttemptEvidence> attempts = new ArrayList<>();
        for (int number = 1; number <= execution.maxAttempts(); number++) {
            int persistedAttemptNumber = nextAttemptNumber(taskId);
            UUID attemptId = startAttempt(taskId, persistedAttemptNumber);
            BuildEvidence build = tool.execute(workspace, MavenCapability.CLEAN_VERIFY);
            if (build.exitCode() == 0 && !build.timedOut()) {
                metrics.validation("success");
                completeAttempt(attemptId, build, "SUCCEEDED", RecoveryDecision.NONE, "real clean verify passed", null);
                attempts.add(new ValidationAttemptEvidence(attemptId, persistedAttemptNumber, build, RecoveryDecision.NONE,
                        "real clean verify passed", null));
                workflow.transition(WorkflowStatus.AWAITING_RELEASE_APPROVAL, Instant.now());
                revision.transition(WorkflowStatus.AWAITING_RELEASE_APPROVAL);
                workflows.save(workflow);
                revisions.save(revision);
                governance.validationPassed(revision.getId());
                finishTask(taskId, "COMPLETED", attempts.size());
                audit(workflowId, revision.getId(), taskId, "VALIDATION_SUCCEEDED", "attempt=" + number);
                return new ValidationOutcome(workflowId, revision.getId(), workflow.getStatus().name(), attempts,
                        false, currentManifest(workspaces, workspace, baseline));
            }

            Optional<UUID> repairId = Optional.empty();
            RecoveryDecision decision;
            String reason;
            if (repairable(build.classification()) && number < execution.maxAttempts()) {
                repairId = repairs.diagnoseProposeValidateAndApply(new RepairCoordinator.RepairContext(
                        workflowId, revision.getId(), workspace, build, analysis.getBaselineManifestHash(), number));
                decision = repairId.isPresent() ? RecoveryDecision.REPAIR : RecoveryDecision.HUMAN_INTERVENTION;
                reason = repairId.isPresent() ? "validated repair proposal applied" : "repair agent produced no safe patch";
            } else if (retryable(build.classification()) && number < execution.maxAttempts()) {
                decision = RecoveryDecision.RETRY;
                reason = "transient validation failure; retrying fixed capability";
                backoff();
            } else {
                decision = number >= execution.maxAttempts() ? RecoveryDecision.TERMINAL_FAILURE : RecoveryDecision.FALLBACK;
                reason = "bounded validation attempts exhausted or failure is non-retryable";
            }
            completeAttempt(attemptId, build, build.timedOut() ? "TIMED_OUT" : "FAILED", decision, reason,
                    repairId.orElse(null));
            metrics.validation("failure");
            metrics.recovery(decision.name(), build.duration());
            attempts.add(new ValidationAttemptEvidence(attemptId, persistedAttemptNumber, build, decision, reason, repairId.orElse(null)));
            audit(workflowId, revision.getId(), taskId, "VALIDATION_FAILED",
                    "attempt=" + number + ",classification=" + build.classification() + ",decision=" + decision);
            if (decision == RecoveryDecision.REPAIR || decision == RecoveryDecision.RETRY) continue;
            if (decision == RecoveryDecision.HUMAN_INTERVENTION) {
                workflow.transition(WorkflowStatus.SAFE_STOPPED, Instant.now());
                revision.transition(WorkflowStatus.SAFE_STOPPED);
                workflows.save(workflow);
                revisions.save(revision);
                finishTask(taskId, "SAFE_STOPPED", attempts.size());
                return new ValidationOutcome(workflowId, revision.getId(), workflow.getStatus().name(), attempts,
                        false, currentManifest(workspaces, workspace, baseline));
            }
            break;
        }

        var rollback = workspaces.rollback(workspace, baseline, analysis.getBaselineManifestHash());
        jdbc.update("insert into rollback_actions(id, revision_id, reason, expected_manifest_hash, "
                        + "restored_manifest_hash, verified, created_at) values (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), revision.getId(), "validation attempts exhausted", rollback.expectedManifestHash(),
                rollback.actualManifestHash(), rollback.restored(), Timestamp.from(Instant.now()));
        workflow.transition(rollback.restored() ? WorkflowStatus.ROLLED_BACK : WorkflowStatus.FAILED, Instant.now());
        metrics.workflowOutcome(workflow.getStatus().name(), Duration.between(workflow.getCreatedAt(), Instant.now()));
        revision.transition(workflow.getStatus());
        workflows.save(workflow);
        revisions.save(revision);
        finishTask(taskId, rollback.restored() ? "ROLLED_BACK" : "FAILED", attempts.size());
        audit(workflowId, revision.getId(), taskId, "ROLLBACK_COMPLETED", "verified=" + rollback.restored());
        return new ValidationOutcome(workflowId, revision.getId(), workflow.getStatus().name(), attempts,
                rollback.restored(), rollback.actualManifestHash());
    }

    private UUID validationTask(UUID revisionId) {
        List<UUID> ids = jdbc.query("select id from agent_tasks where revision_id=? and agent_role='VALIDATION'",
                (rs, row) -> rs.getObject(1, UUID.class), revisionId);
        if (ids.size() == 1) return ids.getFirst();
        if (!ids.isEmpty()) throw new IllegalStateException("revision has duplicate validation tasks");
        UUID taskId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("insert into agent_tasks(id, revision_id, task_key, agent_role, state, attempt_count, created_at, updated_at) "
                        + "values (?, ?, 'validation-execution', 'VALIDATION', 'RUNNING', 0, ?, ?)",
                taskId, revisionId, Timestamp.from(now), Timestamp.from(now));
        return taskId;
    }

    private UUID startAttempt(UUID taskId, int number) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into execution_attempts(id, task_id, attempt_number, executor_type, status, started_at, capability) "
                        + "values (?, ?, ?, 'FIXED_MAVEN_CAPABILITY', 'RUNNING', ?, 'CLEAN_VERIFY')",
                id, taskId, number, Timestamp.from(Instant.now()));
        return id;
    }

    private int nextAttemptNumber(UUID taskId) {
        Integer maximum = jdbc.queryForObject("select coalesce(max(attempt_number), 0) from execution_attempts where task_id=?",
                Integer.class, taskId);
        return maximum + 1;
    }

    private void completeAttempt(UUID id, BuildEvidence build, String status, RecoveryDecision decision,
                                 String reason, UUID repairProposalId) {
        jdbc.update("update execution_attempts set status=?, completed_at=?, failure_classification=?, exit_code=?, "
                        + "duration_ms=?, timed_out=?, stdout_text=?, stderr_text=?, discovered_tests=?, failed_tests=?, "
                        + "coverage_summary=?, recovery_decision=?, decision_reason=?, repair_proposal_id=? where id=?",
                status, Timestamp.from(Instant.now()), build.classification().name(), build.exitCode(),
                build.duration().toMillis(), build.timedOut(), build.stdout(), build.stderr(), build.discoveredTests(),
                build.failedTests(), build.coverageSummary(), decision.name(), reason, repairProposalId, id);
    }

    private void finishTask(UUID taskId, String state, int attempts) {
        jdbc.update("update agent_tasks set state=?, attempt_count=?, updated_at=? where id=?",
                state, attempts, Timestamp.from(Instant.now()), taskId);
    }

    private void audit(UUID workflowId, UUID revisionId, UUID taskId, String type, String details) {
        jdbc.update("insert into audit_events(id, workflow_id, revision_id, task_id, event_type, actor, details, occurred_at) "
                        + "values (?, ?, ?, ?, ?, 'validation-executor', ?, ?)", UUID.randomUUID(), workflowId,
                revisionId, taskId, type, details, Timestamp.from(Instant.now()));
    }

    private boolean repairable(FailureClassification value) {
        return value == FailureClassification.COMPILER || value == FailureClassification.TEST
                || value == FailureClassification.CONFIGURATION;
    }

    private boolean retryable(FailureClassification value) {
        return value == FailureClassification.DEPENDENCY || value == FailureClassification.INFRASTRUCTURE
                || value == FailureClassification.TIMEOUT;
    }

    private void backoff() {
        try { Thread.sleep(validationProperties.retryBackoff().toMillis()); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
    }

    private String currentManifest(RepositoryWorkspaceService workspaces, Path workspace, Path baseline) {
        return workspaces.diff(workspace, baseline).afterManifestHash();
    }
}
