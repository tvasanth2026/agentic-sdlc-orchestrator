package com.vasanth.agenticsdlcorchestrator.governance;

import com.vasanth.agenticsdlcorchestrator.workflow.persistence.WorkflowEntity;
import com.vasanth.agenticsdlcorchestrator.workflow.persistence.WorkflowRevisionEntity;
import com.vasanth.agenticsdlcorchestrator.config.GovernanceProperties;
import com.vasanth.agenticsdlcorchestrator.governance.GovernanceRequests.ApprovalResponse;
import com.vasanth.agenticsdlcorchestrator.governance.GovernanceRequests.OutcomeResponse;
import com.vasanth.agenticsdlcorchestrator.planning.persistence.EngineeringPlanRepository;
import com.vasanth.agenticsdlcorchestrator.requirement.persistence.RequirementAnalysisRepository;
import com.vasanth.agenticsdlcorchestrator.requirement.persistence.RequirementItemEntity.ItemType;
import com.vasanth.agenticsdlcorchestrator.requirement.persistence.RequirementItemRepository;
import com.vasanth.agenticsdlcorchestrator.workflow.domain.WorkflowStatus;
import com.vasanth.agenticsdlcorchestrator.workflow.persistence.WorkflowRepository;
import com.vasanth.agenticsdlcorchestrator.workflow.persistence.WorkflowRevisionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.vasanth.agenticsdlcorchestrator.observability.PlatformMetrics;
import java.time.Duration;

@Service
public class GovernanceService {
    private final WorkflowRepository workflows;
    private final WorkflowRevisionRepository revisions;
    private final EngineeringPlanRepository plans;
    private final RequirementAnalysisRepository analyses;
    private final RequirementItemRepository items;
    private final GovernanceProperties properties;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final PlatformMetrics metrics;

    public GovernanceService(WorkflowRepository workflows, WorkflowRevisionRepository revisions,
                             EngineeringPlanRepository plans, RequirementAnalysisRepository analyses,
                             RequirementItemRepository items, GovernanceProperties properties,
                             JdbcTemplate jdbc, ObjectMapper mapper, PlatformMetrics metrics) {
        this.workflows = workflows; this.revisions = revisions; this.plans = plans; this.analyses = analyses;
        this.items = items; this.properties = properties; this.jdbc = jdbc; this.mapper = mapper;
        this.metrics = metrics;
    }

    public ApprovalResponse approveChange(UUID workflowId, String hash, String token, String approver) {
        requireToken(token, properties.changeApproverToken(), "CHANGE_APPROVER");
        var current = current(workflowId);
        if (current.workflow().getStatus() != WorkflowStatus.AWAITING_CHANGE_APPROVAL)
            throw conflict("workflow is not awaiting change approval");
        String expected = plans.findByRevisionId(current.revision().getId()).orElseThrow().getPlanHash();
        requireExact(hash, expected, "engineering plan");
        persistApproval(current, "CHANGE", hash, approver, "CHANGE_APPROVER");
        jdbc.update("update agent_tasks set state='COMPLETED', updated_at=? where revision_id=? and "
                        + "task_key in ('plan-repository-analysis','plan-architecture','plan-change-approval')",
                Timestamp.from(Instant.now()), current.revision().getId());
        jdbc.update("update agent_tasks set state='READY', updated_at=? where revision_id=? and task_key like 'plan-implement-%'",
                Timestamp.from(Instant.now()), current.revision().getId());
        audit(current, "CHANGE_APPROVED", approver, "exact current plan hash approved");
        return new ApprovalResponse("CHANGE", "APPROVED", current.workflow().getStatus().name(), hash);
    }

    public OutcomeResponse generateOutcome(UUID workflowId) {
        var current = current(workflowId);
        if (current.workflow().getStatus() != WorkflowStatus.AWAITING_RELEASE_APPROVAL)
            throw conflict("workflow has not passed real validation");
        UUID revisionId = current.revision().getId();
        var analysis = analyses.findByRevisionId(revisionId).orElseThrow();
        var criteria = items.findByAnalysisIdOrderByItemTypeAscItemKeyAsc(analysis.getId()).stream()
                .filter(item -> item.getItemType() == ItemType.ACCEPTANCE_CRITERION).toList();
        List<String> production = paths(revisionId, "src/main/%");
        List<String> tests = paths(revisionId, "src/test/%");
        List<String> artifactHashes = jdbc.query("select sha256 from engineering_artifacts where revision_id=? "
                        + "and validation_status='PASSED' order by artifact_key", (rs, row) -> rs.getString(1), revisionId);
        List<String> attempts = jdbc.query("select cast(e.id as varchar) from execution_attempts e join agent_tasks t "
                        + "on t.id=e.task_id where t.revision_id=? and e.executor_type='FIXED_MAVEN_CAPABILITY' order by e.started_at",
                (rs, row) -> rs.getString(1), revisionId);
        boolean buildPassed = Boolean.TRUE.equals(jdbc.queryForObject("select count(*) > 0 from execution_attempts e "
                + "join agent_tasks t on t.id=e.task_id where t.revision_id=? and e.executor_type='FIXED_MAVEN_CAPABILITY' "
                + "and e.status='SUCCEEDED'", Boolean.class, revisionId));
        Integer passedPolicies = jdbc.queryForObject("select count(distinct policy_key) from policy_decisions "
                + "where revision_id=? and decision='ALLOW'", Integer.class, revisionId);
        boolean ready = buildPassed && !production.isEmpty() && !tests.isEmpty() && !artifactHashes.isEmpty()
                && passedPolicies != null && passedPolicies >= 6;
        jdbc.update("delete from criterion_traceability where revision_id=?", revisionId);
        for (var criterion : criteria) {
            List<String> taskIds = jdbc.query("select cast(id as varchar) from agent_tasks where revision_id=? and "
                            + "(task_key like 'plan-implement-%' or task_key like 'plan-test-%' or task_key='plan-validation')",
                    (rs, row) -> rs.getString(1), revisionId);
            List<String> hashes = jdbc.query("select distinct coalesce(a.after_sha256, a.before_sha256) from "
                            + "applied_file_operations a join patch_proposals p on p.id=a.proposal_id where p.revision_id=?",
                    (rs, row) -> rs.getString(1), revisionId);
            String status = ready ? "COMPLETE" : "INCOMPLETE";
            jdbc.update("insert into criterion_traceability(id, revision_id, requirement_id, criterion_id, criterion_text, "
                            + "behavioral, planning_task_ids, production_paths, test_paths, validation_attempt_ids, "
                            + "artifact_hashes, feature_proof, completion_status) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), revisionId, "REV-" + current.revision().getRevisionNumber(), criterion.getItemKey(),
                    criterion.getContent(), criterion.isBehavioral(), json(taskIds), json(production), json(tests),
                    json(attempts), json(hashes), buildPassed ? "Real Maven clean verify passed generated tests" : "missing",
                    status);
        }
        Map<String, Object> outcome = new LinkedHashMap<>();
        outcome.put("workflowId", workflowId); outcome.put("revisionId", revisionId);
        outcome.put("originalRequirement", current.workflow().getOriginalRequirement());
        outcome.put("normalizedRequirement", analysis.getNormalizedProblem());
        outcome.put("workflowRevision", current.revision().getRevisionNumber());
        outcome.put("acceptanceCriteria", criteria.stream().map(item -> Map.of("id", item.getItemKey(),
                "text", item.getContent(), "behavioral", item.isBehavioral())).toList());
        outcome.put("planHash", plans.findByRevisionId(revisionId).orElseThrow().getPlanHash());
        outcome.put("productionFiles", production); outcome.put("testFiles", tests);
        outcome.put("artifactHashes", artifactHashes);
        outcome.put("taskGraph", jdbc.queryForList("select task_key, agent_role, state, attempt_count from agent_tasks where revision_id=? order by created_at", revisionId));
        outcome.put("architecture", jdbc.queryForList("select output_json, output_hash from agent_invocations where revision_id=? and agent_role='ARCHITECTURE'", revisionId));
        outcome.put("appliedDiffs", jdbc.queryForList("select proposal_hash, applied_manifest_hash, unified_diff from patch_proposals where revision_id=? order by created_at", revisionId));
        outcome.put("validationAttempts", jdbc.queryForList("select e.attempt_number, e.status, e.failure_classification, e.exit_code, e.duration_ms, e.discovered_tests, e.failed_tests, e.coverage_summary, e.recovery_decision, e.repair_proposal_id from execution_attempts e join agent_tasks t on t.id=e.task_id where t.revision_id=? order by e.started_at", revisionId));
        outcome.put("policies", jdbc.queryForList("select policy_key, decision, reason from policy_decisions where revision_id=? order by created_at", revisionId));
        outcome.put("approvals", jdbc.queryForList("select gate, evidence_hash, approver, decision, role from approvals where revision_id=? and invalidated_at is null order by created_at", revisionId));
        outcome.put("risks", items.findByAnalysisIdOrderByItemTypeAscItemKeyAsc(analysis.getId()).stream()
                .filter(item -> item.getItemType() == ItemType.RISK).map(item -> item.getContent()).toList());
        outcome.put("assumptions", items.findByAnalysisIdOrderByItemTypeAscItemKeyAsc(analysis.getId()).stream()
                .filter(item -> item.getItemType() == ItemType.ASSUMPTION).map(item -> item.getContent()).toList());
        outcome.put("repairs", jdbc.queryForList("select id, proposal_hash, applied_manifest_hash from patch_proposals where revision_id=? and agent_role='REPAIR'", revisionId));
        outcome.put("rollbacks", jdbc.queryForList("select reason, expected_manifest_hash, restored_manifest_hash, verified from rollback_actions where revision_id=?", revisionId));
        outcome.put("releaseReady", ready);
        String json = json(outcome); String hash = sha256(json);
        jdbc.update("update agent_tasks set state='COMPLETED', updated_at=? where revision_id=? and "
                        + "task_key in ('plan-risk-review','plan-documentation','plan-release-readiness')",
                Timestamp.from(Instant.now()), revisionId);
        jdbc.update("update agent_tasks set state='READY', updated_at=? where revision_id=? and task_key='plan-release-approval'",
                Timestamp.from(Instant.now()), revisionId);
        Instant outcomeTime = Instant.now();
        int updated = jdbc.update("update engineering_outcomes set outcome_json=?, outcome_hash=?, release_ready=?, "
                        + "created_at=? where revision_id=?", json, hash, ready, Timestamp.from(outcomeTime), revisionId);
        if (updated == 0) jdbc.update("insert into engineering_outcomes(id, revision_id, outcome_json, outcome_hash, "
                        + "release_ready, created_at) values (?, ?, ?, ?, ?, ?)", UUID.randomUUID(), revisionId,
                json, hash, ready, Timestamp.from(outcomeTime));
        audit(current, "OUTCOME_GENERATED", "outcome-generator", "releaseReady=" + ready + ",hash=" + hash);
        return new OutcomeResponse(current.workflow().getStatus().name(), hash, ready, json);
    }

    public ApprovalResponse approveRelease(UUID workflowId, String hash, String token, String approver) {
        requireToken(token, properties.releaseApproverToken(), "RELEASE_APPROVER");
        var current = current(workflowId);
        if (current.workflow().getStatus() != WorkflowStatus.AWAITING_RELEASE_APPROVAL)
            throw conflict("workflow is not awaiting release approval");
        var rows = jdbc.query("select outcome_hash, release_ready from engineering_outcomes where revision_id=?",
                (rs, row) -> Map.entry(rs.getString(1), rs.getBoolean(2)), current.revision().getId());
        if (rows.size() != 1 || !rows.getFirst().getValue()) throw conflict("current outcome is incomplete");
        requireExact(hash, rows.getFirst().getKey(), "engineering outcome");
        persistApproval(current, "RELEASE", hash, approver, "RELEASE_APPROVER");
        current.workflow().transition(WorkflowStatus.RELEASE_READY, Instant.now());
        current.revision().transition(WorkflowStatus.RELEASE_READY);
        workflows.save(current.workflow()); revisions.save(current.revision());
        jdbc.update("update agent_tasks set state='COMPLETED', updated_at=? where revision_id=? and task_key='plan-release-approval'",
                Timestamp.from(Instant.now()), current.revision().getId());
        audit(current, "RELEASE_APPROVED", approver, "exact current outcome hash approved");
        metrics.workflowOutcome(WorkflowStatus.RELEASE_READY.name(),
                Duration.between(current.workflow().getCreatedAt(), Instant.now()));
        return new ApprovalResponse("RELEASE", "APPROVED", WorkflowStatus.RELEASE_READY.name(), hash);
    }

    public ApprovalResponse cancel(UUID workflowId, String token, String operator) {
        requireToken(token, properties.operatorToken(), "OPERATOR");
        var current = current(workflowId);
        if (List.of(WorkflowStatus.RELEASE_READY, WorkflowStatus.ROLLED_BACK, WorkflowStatus.FAILED).contains(current.workflow().getStatus()))
            throw conflict("terminal workflow cannot be cancelled");
        current.workflow().transition(WorkflowStatus.SAFE_STOPPED, Instant.now());
        current.revision().transition(WorkflowStatus.SAFE_STOPPED);
        workflows.save(current.workflow()); revisions.save(current.revision());
        jdbc.update("update agent_tasks set state='CANCELLED', updated_at=? where revision_id=? and state not in ('COMPLETED','FAILED','ROLLED_BACK')",
                Timestamp.from(Instant.now()), current.revision().getId());
        audit(current, "WORKFLOW_CANCELLED", operator, "operator requested safe stop");
        metrics.workflowOutcome(WorkflowStatus.SAFE_STOPPED.name(),
                Duration.between(current.workflow().getCreatedAt(), Instant.now()));
        return new ApprovalResponse("CANCELLATION", "APPROVED", WorkflowStatus.SAFE_STOPPED.name(), "none");
    }

    public void requireCurrentChangeApproval(UUID revisionId, String planHash) {
        Integer count = jdbc.queryForObject("select count(*) from approvals where revision_id=? and gate='CHANGE' "
                + "and evidence_hash=? and decision='APPROVED' and invalidated_at is null", Integer.class, revisionId, planHash);
        if (count == null || count != 1) throw conflict("exact current plan requires change approval");
    }

    public void recordPatchPolicies(UUID revisionId) {
        for (String key : List.of("REPOSITORY_BOUNDARY", "PATCH_OPERATIONS", "SECRET_DETECTION",
                "CHANGE_CONTROL", "NO_AUTOMATIC_PUSH", "NO_AUTOMATIC_DEPLOYMENT")) {
            jdbc.update("insert into policy_decisions(id, revision_id, task_id, policy_key, decision, reason, created_at) "
                    + "values (?, ?, null, ?, 'ALLOW', ?, ?)", UUID.randomUUID(), revisionId, key,
                    "validated before isolated workspace mutation", Timestamp.from(Instant.now()));
        }
    }

    public void patchApplied(UUID revisionId) {
        Instant now = Instant.now();
        jdbc.update("update agent_tasks set state='COMPLETED', updated_at=? where revision_id=? and "
                        + "(task_key like 'plan-implement-%' or task_key like 'plan-test-%')",
                Timestamp.from(now), revisionId);
        jdbc.update("update agent_tasks set state='READY', updated_at=? where revision_id=? and task_key='plan-validation'",
                Timestamp.from(now), revisionId);
    }

    public void validationPassed(UUID revisionId) {
        Instant now = Instant.now();
        jdbc.update("update agent_tasks set state='COMPLETED', updated_at=? where revision_id=? and task_key='plan-validation'",
                Timestamp.from(now), revisionId);
        jdbc.update("update agent_tasks set state='READY', updated_at=? where revision_id=? and "
                        + "task_key in ('plan-risk-review','plan-documentation')", Timestamp.from(now), revisionId);
    }

    private List<String> paths(UUID revisionId, String pattern) {
        return jdbc.query("select distinct a.relative_path from applied_file_operations a join patch_proposals p "
                + "on p.id=a.proposal_id where p.revision_id=? and a.relative_path like ? order by a.relative_path",
                (rs, row) -> rs.getString(1), revisionId, pattern);
    }
    private Current current(UUID workflowId) {
        var workflow = workflows.findById(workflowId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var revision = revisions.findByWorkflowIdAndRevisionNumber(workflowId, workflow.getCurrentRevision()).orElseThrow();
        return new Current(workflow, revision);
    }
    private void persistApproval(Current c, String gate, String hash, String approver, String role) {
        jdbc.update("insert into approvals(id, revision_id, gate, evidence_hash, approver, decision, created_at, "
                        + "workflow_revision, role) values (?, ?, ?, ?, ?, 'APPROVED', ?, ?, ?)", UUID.randomUUID(),
                c.revision().getId(), gate, hash, approver, Timestamp.from(Instant.now()), c.revision().getRevisionNumber(), role);
    }
    private void audit(Current c, String type, String actor, String details) {
        jdbc.update("insert into audit_events(id, workflow_id, revision_id, task_id, event_type, actor, details, occurred_at) "
                + "values (?, ?, ?, null, ?, ?, ?, ?)", UUID.randomUUID(), c.workflow().getId(), c.revision().getId(),
                type, actor, details, Timestamp.from(Instant.now()));
    }
    private void requireToken(String actual, String expected, String role) {
        if (!MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, role + " authentication failed");
    }
    private void requireExact(String actual, String expected, String name) {
        if (!MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8)))
            throw conflict(name + " hash is stale, invented, or incorrect");
    }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private String json(Object value) { try { return mapper.writeValueAsString(value); } catch (JacksonException e) { throw new IllegalStateException(e); } }
    private String sha256(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
    private record Current(WorkflowEntity workflow,
                           WorkflowRevisionEntity revision) {}
}
