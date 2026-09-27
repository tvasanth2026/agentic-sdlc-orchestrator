package com.vasanth.agenticsdlcorchestrator.agent;

import com.vasanth.agenticsdlcorchestrator.agent.SpecialistAgentModels.SpecialistAgentInput;
import com.vasanth.agenticsdlcorchestrator.agent.SpecialistAgentModels.SpecialistAgentResult;

public interface SpecialistAgent {
    SpecialistAgentRole role();
    SpecialistAgentResult execute(SpecialistAgentInput input);
}
