package com.vasanth.agenticsdlcorchestrator.execution;

import com.vasanth.agenticsdlcorchestrator.execution.model.ExecutionModels.ToolRequest;
import com.vasanth.agenticsdlcorchestrator.execution.model.ExecutionModels.ToolResult;

public interface EngineeringTool {
    ToolResult execute(ToolRequest request);
}

