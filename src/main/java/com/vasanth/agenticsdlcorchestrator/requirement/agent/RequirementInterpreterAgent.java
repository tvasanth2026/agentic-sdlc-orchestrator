package com.vasanth.agenticsdlcorchestrator.requirement.agent;

import com.vasanth.agenticsdlcorchestrator.requirement.domain.RequirementAnalysis;

public interface RequirementInterpreterAgent {
    RequirementAnalysis interpret(String requirement, String repositoryPath);
}

