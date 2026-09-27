package com.vasanth.agenticsdlcorchestrator.planning;

import com.vasanth.agenticsdlcorchestrator.planning.domain.PlanModels.AgentRole;
import com.vasanth.agenticsdlcorchestrator.planning.domain.PlanModels.EngineeringTaskPlan;
import com.vasanth.agenticsdlcorchestrator.planning.domain.PlanModels.PlannedTask;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class PlanGraphValidator {
    public EngineeringTaskPlan validate(EngineeringTaskPlan plan) {
        if (plan.tasks().isEmpty()) throw new InvalidPlanException("plan has no tasks");
        Map<String, PlannedTask> byId = new HashMap<>();
        for (PlannedTask task : plan.tasks()) {
            if (task.id() == null || task.id().isBlank()) throw new InvalidPlanException("task id is required");
            if (byId.put(task.id(), task) != null) throw new InvalidPlanException("duplicate task id: " + task.id());
            try { AgentRole.valueOf(task.agentRole()); }
            catch (RuntimeException exception) { throw new InvalidPlanException("unknown agent role: " + task.agentRole()); }
            if (task.exitGate() == null || task.exitGate().isBlank()) {
                throw new InvalidPlanException("task exit gate is required: " + task.id());
            }
        }
        for (PlannedTask task : plan.tasks()) {
            for (String dependency : task.dependencies()) {
                if (!byId.containsKey(dependency)) throw new InvalidPlanException("missing dependency: " + dependency);
                if (dependency.equals(task.id())) throw new InvalidPlanException("self dependency: " + task.id());
            }
        }
        detectCycles(byId);
        requireReachableFromRepositoryRoot(byId);
        requireRole(plan.tasks(), AgentRole.VALIDATION);
        requireRole(plan.tasks(), AgentRole.RISK);
        requireRole(plan.tasks(), AgentRole.DOCUMENTATION);
        requireRole(plan.tasks(), AgentRole.RELEASE_READINESS);
        requireRole(plan.tasks(), AgentRole.CHANGE_APPROVER);
        requireRole(plan.tasks(), AgentRole.RELEASE_APPROVER);
        PlannedTask validation = plan.tasks().stream()
                .filter(task -> task.agentRole().equals(AgentRole.VALIDATION.name())).findFirst().orElseThrow();
        if (!validation.exitGate().equals("BUILD_AND_TEST_PASSED")) {
            throw new InvalidPlanException("validation must require real build and tests");
        }
        return plan;
    }

    private void detectCycles(Map<String, PlannedTask> tasks) {
        Map<String, Visit> visits = new HashMap<>();
        for (String id : tasks.keySet()) visit(id, tasks, visits);
    }

    private void visit(String id, Map<String, PlannedTask> tasks, Map<String, Visit> visits) {
        if (visits.get(id) == Visit.VISITING) throw new InvalidPlanException("cycle detected at: " + id);
        if (visits.get(id) == Visit.VISITED) return;
        visits.put(id, Visit.VISITING);
        for (String dependency : tasks.get(id).dependencies()) visit(dependency, tasks, visits);
        visits.put(id, Visit.VISITED);
    }

    private void requireReachableFromRepositoryRoot(Map<String, PlannedTask> tasks) {
        PlannedTask root = tasks.get("repository-analysis");
        if (root == null || !root.dependencies().isEmpty()) {
            throw new InvalidPlanException("repository-analysis must be the dependency root");
        }
        Map<String, List<String>> dependents = tasks.values().stream()
                .flatMap(task -> task.dependencies().stream().map(dependency -> Map.entry(dependency, task.id())))
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        Set<String> reached = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(root.id());
        while (!queue.isEmpty()) {
            String current = queue.remove();
            if (reached.add(current)) queue.addAll(dependents.getOrDefault(current, List.of()));
        }
        Set<String> unreachable = new HashSet<>(tasks.keySet());
        unreachable.removeAll(reached);
        if (!unreachable.isEmpty()) throw new InvalidPlanException("unreachable tasks: " + unreachable);
    }

    private void requireRole(List<PlannedTask> tasks, AgentRole role) {
        if (tasks.stream().noneMatch(task -> task.agentRole().equals(role.name()))) {
            throw new InvalidPlanException("required role missing: " + role);
        }
    }

    private enum Visit { VISITING, VISITED }
}
