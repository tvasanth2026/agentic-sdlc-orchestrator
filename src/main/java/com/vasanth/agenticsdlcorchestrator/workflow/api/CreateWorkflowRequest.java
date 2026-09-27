package com.vasanth.agenticsdlcorchestrator.workflow.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWorkflowRequest(
        @NotBlank @Size(max = 20_000) String requirement,
        @NotBlank @Size(max = 1_024) String repositoryPath) {
}

