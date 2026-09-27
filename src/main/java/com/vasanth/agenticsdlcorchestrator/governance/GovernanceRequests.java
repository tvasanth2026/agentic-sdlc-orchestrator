package com.vasanth.agenticsdlcorchestrator.governance;

import jakarta.validation.constraints.NotBlank;

public final class GovernanceRequests {
    private GovernanceRequests() {}
    public record ApprovalRequest(@NotBlank String evidenceHash) {}
    public record ApprovalResponse(String gate, String decision, String status, String evidenceHash) {}
    public record OutcomeResponse(String status, String outcomeHash, boolean releaseReady, String outcomeJson) {}
}
