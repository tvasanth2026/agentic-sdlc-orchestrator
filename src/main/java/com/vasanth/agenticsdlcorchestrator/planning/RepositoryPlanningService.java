package com.vasanth.agenticsdlcorchestrator.planning;

import com.vasanth.agenticsdlcorchestrator.planning.domain.PlanModels;
import com.vasanth.agenticsdlcorchestrator.requirement.persistence.RequirementItemEntity;
import com.vasanth.agenticsdlcorchestrator.agent.SpecialistAgentOrchestrator;
import com.vasanth.agenticsdlcorchestrator.config.AgenticExecutionProperties;
import com.vasanth.agenticsdlcorchestrator.config.RepositoryToolProperties;
import com.vasanth.agenticsdlcorchestrator.planning.persistence.EngineeringPlanEntity;
import com.vasanth.agenticsdlcorchestrator.planning.persistence.EngineeringPlanRepository;
import com.vasanth.agenticsdlcorchestrator.planning.persistence.RepositoryAnalysisEntity;
import com.vasanth.agenticsdlcorchestrator.planning.persistence.RepositoryAnalysisRepository;
import com.vasanth.agenticsdlcorchestrator.repository.analysis.RepositoryAnalyzer;
import com.vasanth.agenticsdlcorchestrator.repository.tool.ControlledRepositoryTools;
import com.vasanth.agenticsdlcorchestrator.repository.tool.RepositoryAccessException;
import com.vasanth.agenticsdlcorchestrator.repository.tool.SafePathResolver;
import com.vasanth.agenticsdlcorchestrator.repository.workspace.RepositoryWorkspaceService;
import com.vasanth.agenticsdlcorchestrator.requirement.application.SourceMutationGuard;
import com.vasanth.agenticsdlcorchestrator.requirement.domain.RequirementAnalysis;
import com.vasanth.agenticsdlcorchestrator.requirement.domain.RequirementAnalysis.AcceptanceCriterion;
import com.vasanth.agenticsdlcorchestrator.requirement.domain.RequirementAnalysis.AmbiguityAnalysis;
import com.vasanth.agenticsdlcorchestrator.requirement.domain.RequirementAnalysis.RiskLevel;
import com.vasanth.agenticsdlcorchestrator.requirement.persistence.RequirementAnalysisRepository;
import com.vasanth.agenticsdlcorchestrator.requirement.persistence.RequirementItemEntity.ItemType;
import com.vasanth.agenticsdlcorchestrator.requirement.persistence.RequirementItemRepository;
import com.vasanth.agenticsdlcorchestrator.workflow.api.PlanningResponse;
import com.vasanth.agenticsdlcorchestrator.workflow.domain.WorkflowStatus;
import com.vasanth.agenticsdlcorchestrator.workflow.persistence.WorkflowRepository;
import com.vasanth.agenticsdlcorchestrator.workflow.persistence.WorkflowRevisionRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class RepositoryPlanningService {
    private final RepositoryToolProperties repositoryProperties;
    private final AgenticExecutionProperties executionProperties;
    private final WorkflowRepository workflows;
    private final WorkflowRevisionRepository revisions;
    private final RequirementAnalysisRepository analyses;
    private final RequirementItemRepository items;
    private final RepositoryAnalysisRepository repositoryAnalyses;
    private final EngineeringPlanRepository plans;
    private final SourceMutationGuard mutationGuard;
    private final SpecialistAgentOrchestrator agentOrchestrator;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    public RepositoryPlanningService(RepositoryToolProperties repositoryProperties,
                                     AgenticExecutionProperties executionProperties,
                                     WorkflowRepository workflows, WorkflowRevisionRepository revisions,
                                     RequirementAnalysisRepository analyses, RequirementItemRepository items,
                                     RepositoryAnalysisRepository repositoryAnalyses,
                                     EngineeringPlanRepository plans, SourceMutationGuard mutationGuard,
                                     SpecialistAgentOrchestrator agentOrchestrator,
                                     ObjectMapper objectMapper, JdbcTemplate jdbc) {
        this.repositoryProperties = repositoryProperties;
        this.executionProperties = executionProperties;
        this.workflows = workflows;
        this.revisions = revisions;
        this.analyses = analyses;
        this.items = items;
        this.repositoryAnalyses = repositoryAnalyses;
        this.plans = plans;
        this.mutationGuard = mutationGuard;
        this.agentOrchestrator = agentOrchestrator;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
    }

    @Transactional
    public PlanningResponse analyzeAndPlan(UUID workflowId) {
        var workflow = workflows.findById(workflowId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "workflow not found"));
        if (workflow.getStatus() != WorkflowStatus.PLANNING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "workflow is not ready for repository planning");
        }
        var revision = revisions.findByWorkflowIdAndRevisionNumber(workflowId, workflow.getCurrentRevision())
                .orElseThrow();
        if (plans.findByRevisionId(revision.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "current revision already has a plan");
        }
        mutationGuard.requireAllowed(revision.getId());
        Path repository = resolveApprovedRepository(workflow.getRepositoryPath());
        RepositoryWorkspaceService workspaces = new RepositoryWorkspaceService(
                executionProperties.workspaceRoot(), repositoryProperties);
        var workspace = workspaces.create(workflowId, revision.getRevisionNumber(), repository);
        try {
            ControlledRepositoryTools tools = new ControlledRepositoryTools(
                    new SafePathResolver(Path.of(workspace.repositoryPath())), repositoryProperties);
            RequirementAnalysis requirement = loadRequirement(revision.getId());
            var repositoryMap = new RepositoryAnalyzer(tools).analyze(requirement.acceptanceCriteria());
            var plan = new DynamicTaskPlanner().plan(requirement, repositoryMap);
            String repositoryJson = serialize(repositoryMap);
            String analysisHash = sha256(repositoryJson);
            String planJson = serialize(plan);
            String planHash = sha256(planJson);
            Instant now = Instant.now();
            repositoryAnalyses.save(new RepositoryAnalysisEntity(UUID.randomUUID(), revision.getId(),
                    workspace.repositoryPath(), workspace.baselineManifest().manifestHash(), repositoryJson,
                    analysisHash, now));
            plans.save(new EngineeringPlanEntity(UUID.randomUUID(), revision.getId(), revision.getRequirementHash(),
                    analysisHash, planJson, planHash, now));
            persistPlanGraph(revision.getId(), plan, now);
            var invocations = agentOrchestrator.execute(workflowId, revision.getId(), requirement, repositoryMap,
                    plan, revision.getRequirementHash(), analysisHash, planHash);
            workflow.transition(WorkflowStatus.AWAITING_CHANGE_APPROVAL, now);
            revision.transition(WorkflowStatus.AWAITING_CHANGE_APPROVAL);
            workflows.save(workflow);
            revisions.save(revision);
            return new PlanningResponse(workflowId, revision.getId(), WorkflowStatus.AWAITING_CHANGE_APPROVAL,
                    workspace.repositoryPath(), workspace.baselineManifest().manifestHash(), analysisHash,
                    repositoryMap, planHash, plan, invocations);
        } catch (RuntimeException exception) {
            try {
                workspaces.discard(Path.of(workspace.repositoryPath()));
            } catch (RuntimeException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }

    private void persistPlanGraph(UUID revisionId,
                                  PlanModels.EngineeringTaskPlan plan,
                                  Instant now) {
        Map<String, UUID> ids = new LinkedHashMap<>();
        for (var task : plan.tasks()) {
            UUID id = UUID.randomUUID();
            ids.put(task.id(), id);
            jdbc.update("insert into agent_tasks(id, revision_id, task_key, agent_role, state, attempt_count, created_at, updated_at) "
                            + "values (?, ?, ?, ?, 'PENDING', 0, ?, ?)", id, revisionId, "plan-" + task.id(),
                    task.agentRole(), Timestamp.from(now), Timestamp.from(now));
        }
        for (var task : plan.tasks()) for (String dependency : task.dependencies()) {
            jdbc.update("insert into task_dependencies(task_id, depends_on_task_id) values (?, ?)",
                    ids.get(task.id()), ids.get(dependency));
        }
    }

    private RequirementAnalysis loadRequirement(UUID revisionId) {
        var analysis = analyses.findByRevisionId(revisionId).orElseThrow();
        var allItems = items.findByAnalysisIdOrderByItemTypeAscItemKeyAsc(analysis.getId());
        List<AcceptanceCriterion> criteria = allItems.stream()
                .filter(item -> item.getItemType() == ItemType.ACCEPTANCE_CRITERION)
                .map(item -> new AcceptanceCriterion(item.getItemKey(), item.getContent(), item.isBehavioral())).toList();
        List<String> assumptions = content(allItems, ItemType.ASSUMPTION);
        List<String> constraints = content(allItems, ItemType.CONSTRAINT);
        List<String> risks = content(allItems, ItemType.RISK);
        return new RequirementAnalysis(analysis.getNormalizedProblem(), criteria, assumptions, constraints, risks,
                new AmbiguityAnalysis(false, RiskLevel.LOW, List.of(), List.of()));
    }

    private List<String> content(List<RequirementItemEntity> items,
                                 ItemType type) {
        return items.stream().filter(item -> item.getItemType() == type).map(item -> item.getContent()).toList();
    }

    private Path resolveApprovedRepository(String submittedPath) {
        for (Path configuredRoot : repositoryProperties.approvedRoots()) {
            Path absoluteRoot = configuredRoot.toAbsolutePath().normalize();
            try {
                SafePathResolver resolver = new SafePathResolver(absoluteRoot);
                String relative = submittedPath.replace('\\', '/');
                String rootName = absoluteRoot.getFileName().toString();
                if (relative.startsWith(rootName + "/")) relative = relative.substring(rootName.length() + 1);
                Path candidate = resolver.resolveExisting(relative);
                if (java.nio.file.Files.isDirectory(candidate)) return candidate;
            } catch (RepositoryAccessException ignored) {
                // Try the next explicitly configured root.
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "repository is outside approved roots or does not exist");
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("planning evidence serialization failed", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
