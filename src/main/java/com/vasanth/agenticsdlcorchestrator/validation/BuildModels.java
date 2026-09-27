package com.vasanth.agenticsdlcorchestrator.validation;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

public final class BuildModels {
    private BuildModels() {}
    public enum MavenCapability { CLEAN_VERIFY, CLEAN_TEST }
    public enum FailureClassification { NONE, COMPILER, TEST, DEPENDENCY, CONFIGURATION, INFRASTRUCTURE, TIMEOUT, UNKNOWN }
    public enum RecoveryDecision { NONE, RETRY, REPAIR, FALLBACK, HUMAN_INTERVENTION, ROLLBACK, TERMINAL_FAILURE }
    public record BuildEvidence(MavenCapability capability, int exitCode, Duration duration, boolean timedOut,
                                String stdout, String stderr, FailureClassification classification,
                                int discoveredTests, int failedTests, String coverageSummary) {}
    public record ValidationAttemptEvidence(UUID attemptId, int attemptNumber, BuildEvidence build,
                                            RecoveryDecision decision, String decisionReason, UUID repairProposalId) {}
    public record ValidationOutcome(UUID workflowId, UUID revisionId, String status,
                                    List<ValidationAttemptEvidence> attempts,
                                    boolean baselineVerified, String finalManifestHash) {
        public ValidationOutcome { attempts = List.copyOf(attempts); }
    }
}
