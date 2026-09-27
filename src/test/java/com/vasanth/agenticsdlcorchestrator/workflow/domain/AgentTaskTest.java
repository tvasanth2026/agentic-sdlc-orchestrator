package com.vasanth.agenticsdlcorchestrator.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentTaskTest {
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void requiresExecutorLifecycleAndCompleteEvidence() {
        AgentTask task = new AgentTask(UUID.randomUUID(), UUID.randomUUID(), "architecture", "ARCHITECT", now);

        assertThatThrownBy(() -> task.complete(validEvidence(), now))
                .isInstanceOf(IllegalStateException.class);

        task.markReady(now);
        task.start(now);
        task.complete(validEvidence(), now.plusSeconds(1));

        assertThat(task.state()).isEqualTo(TaskState.COMPLETED);
        assertThat(task.attemptCount()).isOne();
        assertThat(task.completionEvidence()).isNotNull();
    }

    @Test
    void rejectsCompletionWithoutArtifactsValidatorsToolsOrGate() {
        UUID attempt = UUID.randomUUID();
        UUID item = UUID.randomUUID();
        assertThatThrownBy(() -> new CompletionEvidence(attempt, List.of(), List.of(item), true, true))
                .hasMessageContaining("artifacts");
        assertThatThrownBy(() -> new CompletionEvidence(attempt, List.of(item), List.of(), true, true))
                .hasMessageContaining("validation");
        assertThatThrownBy(() -> new CompletionEvidence(attempt, List.of(item), List.of(item), false, true))
                .hasMessageContaining("tools");
        assertThatThrownBy(() -> new CompletionEvidence(attempt, List.of(item), List.of(item), true, false))
                .hasMessageContaining("exit gate");
    }

    private CompletionEvidence validEvidence() {
        return new CompletionEvidence(UUID.randomUUID(), List.of(UUID.randomUUID()),
                List.of(UUID.randomUUID()), true, true);
    }
}

