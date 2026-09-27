package com.vasanth.agenticsdlcorchestrator.execution;

import com.vasanth.agenticsdlcorchestrator.execution.model.ExecutionModels.ModelRequest;
import com.vasanth.agenticsdlcorchestrator.execution.model.ExecutionModels.ModelResponse;

public interface ModelProvider {
    ModelResponse generate(ModelRequest request);
}

