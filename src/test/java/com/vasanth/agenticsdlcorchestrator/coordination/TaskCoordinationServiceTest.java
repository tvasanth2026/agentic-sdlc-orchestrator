package com.vasanth.agenticsdlcorchestrator.coordination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vasanth.agenticsdlcorchestrator.workflow.persistence.WorkflowEntity;
import com.vasanth.agenticsdlcorchestrator.workflow.persistence.WorkflowRepository;
import com.vasanth.agenticsdlcorchestrator.workflow.persistence.WorkflowRevisionEntity;
import com.vasanth.agenticsdlcorchestrator.workflow.persistence.WorkflowRevisionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TaskCoordinationServiceTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired WorkflowRepository workflows;
    @Autowired WorkflowRevisionRepository revisions;

    @Test
    void leaseTakeoverFencesStaleWorkerAndMakesCompletionIdempotent() {
        UUID workflowId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Instant now = Instant.now();
        workflows.save(new WorkflowEntity(workflowId, "requirement", "repository", now));
        revisions.save(new WorkflowRevisionEntity(revisionId, workflowId, 1, null, "a".repeat(64), now));
        jdbc.update("insert into agent_tasks(id, revision_id, task_key, agent_role, state, attempt_count, created_at, updated_at) "
                + "values (?, ?, 'coordination-test', 'IMPLEMENTATION', 'READY', 0, ?, ?)", taskId, revisionId,
                Timestamp.from(now), Timestamp.from(now));

        var metrics = new SimpleMeterRegistry();
        var workerOne = service("worker-one", metrics);
        var first = workerOne.claim(taskId).orElseThrow();
        assertThat(service("worker-two", metrics).claim(taskId)).isEmpty();
        jdbc.update("update task_claims set lease_expires_at=? where task_id=?", Timestamp.from(now.minusSeconds(1)), taskId);

        var workerTwo = service("worker-two", metrics);
        var second = workerTwo.claim(taskId).orElseThrow();
        assertThat(second.fencingToken()).isEqualTo(first.fencingToken() + 1);
        assertThatThrownBy(() -> workerOne.complete(taskId, first.fencingToken(), "effect-1", revisionId, "b".repeat(64)))
                .isInstanceOf(TaskCoordinationService.StaleWorkerException.class);
        assertThat(workerTwo.complete(taskId, second.fencingToken(), "effect-1", revisionId, "b".repeat(64))).isTrue();
        assertThat(jdbc.queryForObject("select state from agent_tasks where id=?", String.class, taskId))
                .isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select count(*) from idempotent_effects where effect_key='effect-1'", Integer.class)).isOne();
        assertThat(metrics.get("agentic.lease.takeovers").counter().count()).isEqualTo(1);
    }

    @Test
    void recoveryRequeuesOnlyExpiredRunningTasksAndPreservesHumanPause() {
        UUID workflowId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        UUID running = UUID.randomUUID();
        UUID paused = UUID.randomUUID();
        Instant now = Instant.now();
        workflows.save(new WorkflowEntity(workflowId, "requirement", "repository", now));
        revisions.save(new WorkflowRevisionEntity(revisionId, workflowId, 1, null, "c".repeat(64), now));
        insertTask(running, revisionId, "RUNNING", now);
        insertTask(paused, revisionId, "AWAITING_APPROVAL", now);
        jdbc.update("insert into task_claims(task_id, worker_id, fencing_token, lease_expires_at, heartbeat_at, claimed_at) values (?, 'dead', 4, ?, ?, ?)",
                running, Timestamp.from(now.minusSeconds(1)), Timestamp.from(now), Timestamp.from(now));

        service("recovery", new SimpleMeterRegistry()).recoverExpiredClaims();
        assertThat(jdbc.queryForObject("select state from agent_tasks where id=?", String.class, running)).isEqualTo("READY");
        assertThat(jdbc.queryForObject("select state from agent_tasks where id=?", String.class, paused)).isEqualTo("AWAITING_APPROVAL");
    }

    private TaskCoordinationService service(String worker, SimpleMeterRegistry metrics) {
        return new TaskCoordinationService(jdbc,
                new CoordinationProperties(worker, Duration.ofSeconds(30), Duration.ofSeconds(10)), metrics);
    }

    private void insertTask(UUID id, UUID revisionId, String state, Instant now) {
        jdbc.update("insert into agent_tasks(id, revision_id, task_key, agent_role, state, attempt_count, created_at, updated_at) "
                        + "values (?, ?, ?, 'IMPLEMENTATION', ?, 0, ?, ?)", id, revisionId, "task-" + id, state,
                Timestamp.from(now), Timestamp.from(now));
    }
}
