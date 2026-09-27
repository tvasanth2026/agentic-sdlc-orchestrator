package com.vasanth.agenticsdlcorchestrator.workflow.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record ClarificationRequest(
        @NotBlank @Size(max = 20_000) String clarifiedRequirement,
        @NotEmpty Map<@NotBlank String, @NotBlank @Size(max = 4_000) String> answers) {
}

