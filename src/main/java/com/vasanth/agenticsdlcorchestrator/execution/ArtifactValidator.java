package com.vasanth.agenticsdlcorchestrator.execution;

import com.vasanth.agenticsdlcorchestrator.artifact.ArtifactModels.EngineeringArtifact;
import com.vasanth.agenticsdlcorchestrator.artifact.ArtifactModels.ValidationResult;
import com.vasanth.agenticsdlcorchestrator.execution.model.ExecutionModels.ValidationContext;

public interface ArtifactValidator {
    ValidationResult validate(EngineeringArtifact artifact, ValidationContext context);
}

