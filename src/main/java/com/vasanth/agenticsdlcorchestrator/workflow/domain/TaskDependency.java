package com.vasanth.agenticsdlcorchestrator.workflow.domain;

import java.util.Objects;
import java.util.UUID;

public record TaskDependency(UUID taskId, UUID dependsOnTaskId) {
    public TaskDependency {
        Objects.requireNonNull(taskId);
        Objects.requireNonNull(dependsOnTaskId);
        if (taskId.equals(dependsOnTaskId)) throw new IllegalArgumentException("task cannot depend on itself");
    }
}

