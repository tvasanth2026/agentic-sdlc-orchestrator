package com.vasanth.agenticsdlcorchestrator.workflow.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class AgentTask {
    private final UUID id;
    private final UUID revisionId;
    private final String taskKey;
    private final String agentRole;
    private TaskState state;
    private int attemptCount;
    private CompletionEvidence completionEvidence;
    private final Instant createdAt;
    private Instant updatedAt;

    public AgentTask(UUID id, UUID revisionId, String taskKey, String agentRole, Instant now) {
        this.id = Objects.requireNonNull(id);
        this.revisionId = Objects.requireNonNull(revisionId);
        this.taskKey = requireText(taskKey, "taskKey");
        this.agentRole = requireText(agentRole, "agentRole");
        this.state = TaskState.PENDING;
        this.createdAt = Objects.requireNonNull(now);
        this.updatedAt = now;
    }

    public void markReady(Instant now) {
        requireState(TaskState.PENDING);
        state = TaskState.READY;
        updatedAt = Objects.requireNonNull(now);
    }

    public void start(Instant now) {
        requireState(TaskState.READY, TaskState.RETRY_SCHEDULED);
        state = TaskState.RUNNING;
        attemptCount++;
        updatedAt = Objects.requireNonNull(now);
    }

    public void complete(CompletionEvidence evidence, Instant now) {
        requireState(TaskState.RUNNING);
        completionEvidence = Objects.requireNonNull(evidence);
        state = TaskState.COMPLETED;
        updatedAt = Objects.requireNonNull(now);
    }

    private void requireState(TaskState... allowed) {
        for (TaskState candidate : allowed) if (state == candidate) return;
        throw new IllegalStateException("task cannot transition from " + state);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    public UUID id() { return id; }
    public UUID revisionId() { return revisionId; }
    public String taskKey() { return taskKey; }
    public String agentRole() { return agentRole; }
    public TaskState state() { return state; }
    public int attemptCount() { return attemptCount; }
    public CompletionEvidence completionEvidence() { return completionEvidence; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}

