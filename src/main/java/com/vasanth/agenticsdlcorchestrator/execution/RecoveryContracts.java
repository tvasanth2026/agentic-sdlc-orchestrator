package com.vasanth.agenticsdlcorchestrator.execution;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

public final class RecoveryContracts {
    private RecoveryContracts() {}

    public interface RetryPolicy {
        RetryDecision evaluate(int attempt, Failure failure);
    }

    public interface FallbackStrategy {
        FallbackResult execute(UUID taskId, Failure failure);
    }

    public interface RollbackAction {
        RollbackResult restore(UUID revisionId, String baselineManifestHash);
    }

    public interface RepositoryWorkspace {
        Path createIsolated(UUID workflowId, UUID revisionId);
        String snapshot(Path workspace);
        boolean verify(Path workspace, String manifestHash);
    }

    public record Failure(String classification, String summary, boolean retryable) {}
    public record RetryDecision(boolean retry, Duration backoff, String reason) {}
    public record FallbackResult(boolean successful, String strategy, String evidenceHash) {}
    public record RollbackResult(boolean successful, String restoredManifestHash) {}
}

