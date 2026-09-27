package com.vasanth.agenticsdlcorchestrator.workflow.domain;

public enum TaskState {
    PENDING,
    READY,
    RUNNING,
    AWAITING_APPROVAL,
    RETRY_SCHEDULED,
    COMPLETED,
    FAILED,
    CANCELLED,
    ROLLED_BACK
}

