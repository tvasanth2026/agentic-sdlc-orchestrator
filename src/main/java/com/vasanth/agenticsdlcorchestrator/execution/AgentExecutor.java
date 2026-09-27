package com.vasanth.agenticsdlcorchestrator.execution;

import com.vasanth.agenticsdlcorchestrator.execution.model.ExecutionModels.AgentExecutionResult;
import com.vasanth.agenticsdlcorchestrator.execution.model.ExecutionModels.ExecutionContext;
import com.vasanth.agenticsdlcorchestrator.workflow.domain.AgentTask;

public interface AgentExecutor {
    AgentExecutionResult execute(AgentTask task, ExecutionContext context);
}

