package com.vasanth.agenticsdlcorchestrator.workflow.api;

import com.vasanth.agenticsdlcorchestrator.agent.SpecialistAgentOrchestrator.AgentInvocationSummary;
import com.vasanth.agenticsdlcorchestrator.planning.domain.PlanModels.EngineeringTaskPlan;
import com.vasanth.agenticsdlcorchestrator.repository.domain.RepositoryModels.RepositoryMap;
import com.vasanth.agenticsdlcorchestrator.workflow.domain.WorkflowStatus;
import java.util.UUID;
import java.util.List;

public record PlanningResponse(UUID workflowId, UUID revisionId, WorkflowStatus status,
                               String workspaceLocation, String baselineManifestHash,
                               String repositoryAnalysisHash, RepositoryMap repositoryAnalysis,
                               String planHash, EngineeringTaskPlan plan,
                               List<AgentInvocationSummary> agentInvocations) {
}
