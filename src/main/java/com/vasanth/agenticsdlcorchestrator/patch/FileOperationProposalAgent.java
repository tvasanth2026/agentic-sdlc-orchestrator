package com.vasanth.agenticsdlcorchestrator.patch;

import com.vasanth.agenticsdlcorchestrator.patch.PatchModels.AgentPatchProposal;
import java.util.List;
import java.util.Optional;

public interface FileOperationProposalAgent {
    List<AgentPatchProposal> propose(ProposalContext context);

    default Optional<AgentPatchProposal> proposeRepair(RepairProposalContext context) {
        return Optional.empty();
    }

    record ProposalContext(String requirementId, String normalizedRequirement,
                           List<String> acceptanceCriterionIds, String requirementHash,
                           String repositoryAnalysisHash, String planHash, String workspaceLocation) {
        public ProposalContext { acceptanceCriterionIds = List.copyOf(acceptanceCriterionIds); }
    }

    record RepairProposalContext(String requirementId, List<String> acceptanceCriterionIds,
                                 String boundedFailureEvidence, String relevantSources,
                                 String priorProposalJson, List<String> inputArtifactHashes) {
        public RepairProposalContext {
            acceptanceCriterionIds = List.copyOf(acceptanceCriterionIds);
            inputArtifactHashes = List.copyOf(inputArtifactHashes);
        }
    }
}
