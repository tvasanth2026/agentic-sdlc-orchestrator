package com.vasanth.agenticsdlcorchestrator.patch;

import com.vasanth.agenticsdlcorchestrator.patch.PatchModels.AgentPatchProposal;
import java.util.List;

public interface FileOperationProposalAgent {
    List<AgentPatchProposal> propose(ProposalContext context);

    record ProposalContext(String requirementId, String normalizedRequirement,
                           List<String> acceptanceCriterionIds, String requirementHash,
                           String repositoryAnalysisHash, String planHash) {
        public ProposalContext { acceptanceCriterionIds = List.copyOf(acceptanceCriterionIds); }
    }
}
