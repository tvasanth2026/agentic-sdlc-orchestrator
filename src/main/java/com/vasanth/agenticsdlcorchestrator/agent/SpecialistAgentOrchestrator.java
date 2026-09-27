package com.vasanth.agenticsdlcorchestrator.agent;

import com.vasanth.agenticsdlcorchestrator.agent.SpecialistAgentModels.SpecialistAgentInput;
import com.vasanth.agenticsdlcorchestrator.agent.SpecialistAgentModels.SpecialistOutput;
import com.vasanth.agenticsdlcorchestrator.planning.domain.PlanModels.EngineeringTaskPlan;
import com.vasanth.agenticsdlcorchestrator.repository.domain.RepositoryModels.RepositoryMap;
import com.vasanth.agenticsdlcorchestrator.requirement.domain.RequirementAnalysis;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class SpecialistAgentOrchestrator {
    private final List<SpecialistAgent> agents;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public SpecialistAgentOrchestrator(List<SpecialistAgent> agents, JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.agents = agents.stream().sorted(Comparator.comparing(agent -> agent.role().ordinal())).toList();
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<AgentInvocationSummary> execute(UUID workflowId, UUID revisionId,
                                                RequirementAnalysis requirement, RepositoryMap repository,
                                                EngineeringTaskPlan plan, String requirementHash,
                                                String repositoryHash, String planHash) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("requirement", requirement);
        context.put("repositoryAnalysis", repository);
        context.put("engineeringPlan", plan);
        context.put("validationPassed", false);
        List<String> hashes = List.of(requirementHash, repositoryHash, planHash);
        List<AgentInvocationSummary> summaries = new ArrayList<>();
        UUID previousTaskId = null;
        for (SpecialistAgent agent : agents) {
            UUID taskId = UUID.randomUUID();
            UUID attemptId = UUID.randomUUID();
            UUID artifactId = UUID.randomUUID();
            UUID validationId = UUID.randomUUID();
            UUID invocationId = UUID.randomUUID();
            Instant started = Instant.now();
            insertRunningTask(taskId, revisionId, agent.role(), started);
            if (previousTaskId != null) {
                jdbc.update("insert into task_dependencies(task_id, depends_on_task_id) values (?, ?)",
                        taskId, previousTaskId);
            }
            jdbc.update("insert into execution_attempts(id, task_id, attempt_number, executor_type, status, started_at) "
                            + "values (?, ?, 1, ?, 'RUNNING', ?)",
                    attemptId, taskId, "MODEL_SPECIALIST", timestamp(started));
            var result = agent.execute(new SpecialistAgentInput(requirement.normalizedProblem(), context, hashes));
            Instant completed = Instant.now();
            String artifactKey = "specialist-" + agent.role().name().toLowerCase().replace('_', '-');
            jdbc.update("insert into engineering_artifacts(id, revision_id, task_id, artifact_type, artifact_key, "
                            + "schema_version, storage_location, sha256, validation_status, created_at, input_hashes, "
                            + "producing_agent, provider, model) values (?, ?, ?, ?, ?, '1.0', ?, ?, 'PASSED', ?, ?, ?, ?, ?)",
                    artifactId, revisionId, taskId, "SPECIALIST_OUTPUT", artifactKey,
                    "db://agent-invocations/" + invocationId, result.outputHash(), timestamp(completed), serialize(hashes),
                    agent.role().name(), result.provider(), result.model());
            jdbc.update("insert into validation_results(id, artifact_id, validator, status, summary, evidence_location, created_at) "
                            + "values (?, ?, 'specialist-output-schema', 'PASSED', ?, ?, ?)",
                    validationId, artifactId, "Strict specialist output schema passed",
                    "classpath:/schemas/specialist-output.schema.json", timestamp(completed));
            jdbc.update("insert into agent_invocations(id, workflow_id, revision_id, task_id, agent_role, provider, model, "
                            + "attempt_number, input_artifact_hashes, output_hash, decisions, assumptions, risks, duration_ms, "
                            + "generated_artifact_ids, output_json, created_at) values (?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    invocationId, workflowId, revisionId, taskId, agent.role().name(), result.provider(), result.model(),
                    serialize(hashes), result.outputHash(), serialize(result.output().decisions()),
                    serialize(result.output().assumptions()), serialize(result.output().risks()),
                    result.duration().toMillis(), serialize(List.of(artifactId)), result.outputJson(), timestamp(completed));
            jdbc.update("update execution_attempts set status='SUCCEEDED', completed_at=? where id=?",
                    timestamp(completed), attemptId);
            jdbc.update("update agent_tasks set state='COMPLETED', attempt_count=1, updated_at=? where id=?",
                    timestamp(completed), taskId);
            summaries.add(new AgentInvocationSummary(invocationId, taskId, agent.role(), result.provider(),
                    result.model(), artifactId, result.outputHash(), result.output(), result.duration().toMillis()));
            previousTaskId = taskId;
        }
        return List.copyOf(summaries);
    }

    private void insertRunningTask(UUID taskId, UUID revisionId, SpecialistAgentRole role, Instant now) {
        jdbc.update("insert into agent_tasks(id, revision_id, task_key, agent_role, state, attempt_count, created_at, updated_at) "
                        + "values (?, ?, ?, ?, 'RUNNING', 1, ?, ?)",
                taskId, revisionId, "specialist-" + role.name().toLowerCase().replace('_', '-'), role.name(),
                timestamp(now), timestamp(now));
    }

    private Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("agent evidence serialization failed", exception);
        }
    }

    public record AgentInvocationSummary(UUID invocationId, UUID taskId, SpecialistAgentRole role,
                                         String provider, String model, UUID artifactId, String outputHash,
                                         SpecialistOutput output, long durationMs) {}
}
