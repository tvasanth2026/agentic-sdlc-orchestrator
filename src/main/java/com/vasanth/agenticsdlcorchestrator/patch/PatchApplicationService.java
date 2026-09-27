package com.vasanth.agenticsdlcorchestrator.patch;

import com.vasanth.agenticsdlcorchestrator.config.AgenticExecutionProperties;
import com.vasanth.agenticsdlcorchestrator.config.PatchPolicyProperties;
import com.vasanth.agenticsdlcorchestrator.config.RepositoryToolProperties;
import com.vasanth.agenticsdlcorchestrator.patch.FileOperationProposalAgent.ProposalContext;
import com.vasanth.agenticsdlcorchestrator.patch.PatchModels.AgentPatchProposal;
import com.vasanth.agenticsdlcorchestrator.patch.PatchModels.AppliedFileOperation;
import com.vasanth.agenticsdlcorchestrator.patch.PatchModels.PatchApplicationResult;
import com.vasanth.agenticsdlcorchestrator.planning.persistence.EngineeringPlanRepository;
import com.vasanth.agenticsdlcorchestrator.planning.persistence.RepositoryAnalysisRepository;
import com.vasanth.agenticsdlcorchestrator.repository.workspace.RepositoryWorkspaceService;
import com.vasanth.agenticsdlcorchestrator.requirement.persistence.RequirementAnalysisRepository;
import com.vasanth.agenticsdlcorchestrator.requirement.persistence.RequirementItemEntity.ItemType;
import com.vasanth.agenticsdlcorchestrator.requirement.persistence.RequirementItemRepository;
import com.vasanth.agenticsdlcorchestrator.workflow.api.ApplyChangesRequest;
import com.vasanth.agenticsdlcorchestrator.workflow.api.ApplyChangesResponse;
import com.vasanth.agenticsdlcorchestrator.workflow.domain.WorkflowStatus;
import com.vasanth.agenticsdlcorchestrator.workflow.persistence.WorkflowRepository;
import com.vasanth.agenticsdlcorchestrator.workflow.persistence.WorkflowRevisionRepository;
import com.vasanth.agenticsdlcorchestrator.governance.GovernanceService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class PatchApplicationService {
    private final WorkflowRepository workflows;
    private final WorkflowRevisionRepository revisions;
    private final EngineeringPlanRepository plans;
    private final RepositoryAnalysisRepository repositoryAnalyses;
    private final RequirementAnalysisRepository requirementAnalyses;
    private final RequirementItemRepository requirementItems;
    private final FileOperationProposalAgent proposalAgent;
    private final AgenticExecutionProperties executionProperties;
    private final RepositoryToolProperties repositoryProperties;
    private final PatchPolicyProperties patchProperties;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final GovernanceService governance;

    public PatchApplicationService(WorkflowRepository workflows, WorkflowRevisionRepository revisions,
                                   EngineeringPlanRepository plans, RepositoryAnalysisRepository repositoryAnalyses,
                                   RequirementAnalysisRepository requirementAnalyses,
                                   RequirementItemRepository requirementItems,
                                   FileOperationProposalAgent proposalAgent,
                                   AgenticExecutionProperties executionProperties,
                                   RepositoryToolProperties repositoryProperties,
                                   PatchPolicyProperties patchProperties, JdbcTemplate jdbc,
                                   ObjectMapper objectMapper, GovernanceService governance) {
        this.workflows = workflows;
        this.revisions = revisions;
        this.plans = plans;
        this.repositoryAnalyses = repositoryAnalyses;
        this.requirementAnalyses = requirementAnalyses;
        this.requirementItems = requirementItems;
        this.proposalAgent = proposalAgent;
        this.executionProperties = executionProperties;
        this.repositoryProperties = repositoryProperties;
        this.patchProperties = patchProperties;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.governance = governance;
    }

    @Transactional
    public ApplyChangesResponse generateAndApply(UUID workflowId, ApplyChangesRequest request) {
        var workflow = workflows.findById(workflowId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "workflow not found"));
        if (workflow.getStatus() != WorkflowStatus.AWAITING_CHANGE_APPROVAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "workflow is not awaiting change approval");
        }
        var revision = revisions.findByWorkflowIdAndRevisionNumber(workflowId, workflow.getCurrentRevision())
                .orElseThrow();
        var plan = plans.findByRevisionId(revision.getId()).orElseThrow();
        if (!plan.getPlanHash().equals(request.planHash())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "plan hash is stale or incorrect");
        }
        governance.requireCurrentChangeApproval(revision.getId(), plan.getPlanHash());
        governance.recordPatchPolicies(revision.getId());
        var repositoryAnalysis = repositoryAnalyses.findByRevisionId(revision.getId()).orElseThrow();
        var requirementAnalysis = requirementAnalyses.findByRevisionId(revision.getId()).orElseThrow();
        List<String> criteria = requirementItems.findByAnalysisIdOrderByItemTypeAscItemKeyAsc(requirementAnalysis.getId())
                .stream().filter(item -> item.getItemType() == ItemType.ACCEPTANCE_CRITERION)
                .map(item -> item.getItemKey()).toList();
        ProposalContext context = new ProposalContext("REV-" + revision.getRevisionNumber(),
                requirementAnalysis.getNormalizedProblem(), criteria, plan.getRequirementHash(),
                plan.getRepositoryAnalysisHash(), plan.getPlanHash(), repositoryAnalysis.getWorkspaceLocation());
        List<AgentPatchProposal> proposals = proposalAgent.propose(context);
        Path repository = Path.of(repositoryAnalysis.getWorkspaceLocation());
        Path baseline = repository.getParent().resolve("snapshots").resolve("baseline");
        RepositoryWorkspaceService workspaces = new RepositoryWorkspaceService(
                executionProperties.workspaceRoot(), repositoryProperties);
        GovernedPatchApplier applier = new GovernedPatchApplier(repository, baseline, workspaces,
                repositoryProperties, patchProperties);
        List<UUID> proposalIds = new ArrayList<>();
        List<AppliedFileOperation> applied = new ArrayList<>();
        PatchApplicationResult finalResult = null;
        try {
            for (AgentPatchProposal proposal : proposals) {
                String proposalJson = serialize(proposal);
                String proposalHash = sha256(proposalJson);
                PatchApplicationResult result = applier.apply(proposal, proposalHash,
                        repositoryAnalysis.getBaselineManifestHash());
                persist(revision.getId(), proposal, proposalJson, result, repositoryAnalysis.getBaselineManifestHash());
                proposalIds.add(proposal.id());
                applied.addAll(result.operations());
                finalResult = result;
            }
            if (finalResult == null) throw new PatchPolicyException("proposal agent returned no proposals");
            governance.patchApplied(revision.getId());
            Instant now = Instant.now();
            workflow.transition(WorkflowStatus.EXECUTING, now);
            revision.transition(WorkflowStatus.EXECUTING);
            workflows.save(workflow);
            revisions.save(revision);
            return new ApplyChangesResponse(workflowId, revision.getId(), WorkflowStatus.EXECUTING,
                    proposalIds, finalResult.sourceManifest().manifestHash(), finalResult.diff().changedPaths(),
                    finalResult.diff().unifiedDiff(), applied);
        } catch (RuntimeException exception) {
            try { workspaces.rollback(repository, baseline, repositoryAnalysis.getBaselineManifestHash()); }
            catch (RuntimeException rollbackFailure) { exception.addSuppressed(rollbackFailure); }
            throw exception;
        }
    }

    public UUID applyRepairProposal(UUID revisionId, AgentPatchProposal proposal) {
        if (!"REPAIR".equals(proposal.agentRole())) throw new PatchPolicyException("repair proposal must use REPAIR role");
        var repositoryAnalysis = repositoryAnalyses.findByRevisionId(revisionId).orElseThrow();
        Path repository = Path.of(repositoryAnalysis.getWorkspaceLocation());
        Path baseline = repository.getParent().resolve("snapshots").resolve("baseline");
        RepositoryWorkspaceService workspaces = new RepositoryWorkspaceService(
                executionProperties.workspaceRoot(), repositoryProperties);
        GovernedPatchApplier applier = new GovernedPatchApplier(repository, baseline, workspaces,
                repositoryProperties, patchProperties);
        String proposalJson = serialize(proposal);
        String proposalHash = sha256(proposalJson);
        PatchApplicationResult result = applier.apply(proposal, proposalHash,
                repositoryAnalysis.getBaselineManifestHash());
        persist(revisionId, proposal, proposalJson, result, repositoryAnalysis.getBaselineManifestHash());
        return proposal.id();
    }

    private void persist(UUID revisionId, AgentPatchProposal proposal, String json,
                         PatchApplicationResult result, String baselineHash) {
        Instant now = Instant.now();
        jdbc.update("insert into patch_proposals(id, revision_id, agent_role, provider, model, proposal_json, "
                        + "proposal_hash, baseline_manifest_hash, applied_manifest_hash, unified_diff, status, created_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'APPLIED', ?)",
                proposal.id(), revisionId, proposal.agentRole(), proposal.provider(), proposal.model(), json,
                result.proposalHash(), baselineHash, result.sourceManifest().manifestHash(),
                result.diff().unifiedDiff(), Timestamp.from(now));
        for (int index = 0; index < proposal.operations().size(); index++) {
            var operation = proposal.operations().get(index);
            String contentHash = operation.content() == null ? null : sha256(operation.content());
            jdbc.update("insert into proposed_file_operations(id, proposal_id, operation_index, relative_path, "
                            + "operation_type, expected_sha256, content_sha256, reason, requirement_id, "
                            + "acceptance_criterion_ids, task_id, input_artifact_hashes) "
                            + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), proposal.id(), index, operation.relativePath(), operation.operationType().name(),
                    operation.expectedSha256(), contentHash, operation.reason(), operation.requirementId(),
                    serialize(operation.acceptanceCriterionIds()), operation.taskId(),
                    serialize(operation.inputArtifactHashes()));
        }
        for (AppliedFileOperation operation : result.operations()) {
            jdbc.update("insert into applied_file_operations(id, proposal_id, relative_path, operation_type, "
                            + "before_sha256, after_sha256, applied_at) values (?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), proposal.id(), operation.relativePath(), operation.operationType().name(),
                    operation.beforeSha256(), operation.afterSha256(), Timestamp.from(now));
        }
    }

    private String serialize(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JacksonException exception) { throw new IllegalStateException("patch evidence serialization failed", exception); }
    }

    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
}
