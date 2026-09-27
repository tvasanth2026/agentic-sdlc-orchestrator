package com.vasanth.agenticsdlcorchestrator.planning.domain;

import java.util.List;
import java.util.Map;

public final class PlanModels {
    private PlanModels() {}

    public enum AgentRole {
        REPOSITORY, ARCHITECTURE, IMPLEMENTATION, TEST, VALIDATION,
        RISK, DOCUMENTATION, CHANGE_APPROVER, RELEASE_READINESS, RELEASE_APPROVER
    }

    public record PlannedTask(
            String id,
            String title,
            String agentRole,
            List<String> dependencies,
            List<String> acceptanceCriterionIds,
            List<String> impactedComponents,
            String entryGate,
            String exitGate) {
        public PlannedTask {
            dependencies = List.copyOf(dependencies);
            acceptanceCriterionIds = List.copyOf(acceptanceCriterionIds);
            impactedComponents = List.copyOf(impactedComponents);
        }
    }

    public record EngineeringTaskPlan(String requirementHash, List<PlannedTask> tasks,
                                      Map<String, List<String>> executionLayers) {
        public EngineeringTaskPlan {
            tasks = List.copyOf(tasks);
            executionLayers = Map.copyOf(executionLayers);
        }
    }
}

