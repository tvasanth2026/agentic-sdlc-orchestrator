package com.vasanth.agenticsdlcorchestrator.execution.model;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ExecutionModels {
    private ExecutionModels() {}

    public record ExecutionContext(UUID workflowId, UUID revisionId, String workspaceLocation,
                                   Map<String, String> boundedContext) {}
    public record AgentExecutionResult(boolean successful, List<UUID> artifactIds, String outputHash) {}
    public record ToolRequest(String capability, Map<String, String> arguments) {}
    public record ToolResult(boolean successful, int exitCode, Duration duration, String stdout, String stderr) {}
    public record ValidationContext(UUID workflowId, UUID revisionId, String workspaceLocation) {}
    public record ModelRequest(String agentRole, String schemaName, String instructions,
                               Map<String, Object> context, int maxOutputCharacters) {}
    public record ModelResponse(String provider, String model, String structuredOutput, Duration duration) {}
    public record ExecutionAttempt(UUID id, UUID taskId, int attemptNumber, String executorType,
                                   AttemptStatus status, Instant startedAt, Instant completedAt,
                                   String failureClassification) {}
    public enum AttemptStatus { RUNNING, SUCCEEDED, FAILED, TIMED_OUT, ROLLED_BACK }
}
