package com.vasanth.agenticsdlcorchestrator.workflow.domain;

public enum WorkflowStatus {
    RECEIVED,
    AWAITING_CLARIFICATION,
    PLANNING,
    AWAITING_CHANGE_APPROVAL,
    EXECUTING,
    VALIDATING,
    AWAITING_RELEASE_APPROVAL,
    RELEASE_READY,
    SAFE_STOPPED,
    FAILED,
    ROLLED_BACK
}

