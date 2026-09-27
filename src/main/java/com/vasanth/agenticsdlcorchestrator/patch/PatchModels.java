package com.vasanth.agenticsdlcorchestrator.patch;

import com.vasanth.agenticsdlcorchestrator.repository.domain.RepositoryModels.DiffEvidence;
import com.vasanth.agenticsdlcorchestrator.repository.domain.RepositoryModels.SourceManifest;
import java.util.List;
import java.util.UUID;

public final class PatchModels {
    private PatchModels() {}

    public enum FileOperationType { CREATE, UPDATE, DELETE }

    public record FileOperation(String relativePath, FileOperationType operationType, String content,
                                String expectedSha256, String reason, String requirementId,
                                List<String> acceptanceCriterionIds, String taskId,
                                List<String> inputArtifactHashes) {
        public FileOperation {
            acceptanceCriterionIds = List.copyOf(acceptanceCriterionIds);
            inputArtifactHashes = List.copyOf(inputArtifactHashes);
        }
    }

    public record AgentPatchProposal(UUID id, String agentRole, String provider, String model,
                                     List<FileOperation> operations) {
        public AgentPatchProposal { operations = List.copyOf(operations); }
    }

    public record AppliedFileOperation(String relativePath, FileOperationType operationType,
                                       String beforeSha256, String afterSha256) {}

    public record PatchApplicationResult(String proposalHash, SourceManifest sourceManifest,
                                         DiffEvidence diff, List<AppliedFileOperation> operations) {
        public PatchApplicationResult { operations = List.copyOf(operations); }
    }
}
