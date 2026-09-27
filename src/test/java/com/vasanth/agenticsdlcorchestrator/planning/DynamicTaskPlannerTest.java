package com.vasanth.agenticsdlcorchestrator.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vasanth.agenticsdlcorchestrator.planning.domain.PlanModels.EngineeringTaskPlan;
import com.vasanth.agenticsdlcorchestrator.planning.domain.PlanModels.PlannedTask;
import com.vasanth.agenticsdlcorchestrator.repository.domain.RepositoryModels.RepositoryMap;
import com.vasanth.agenticsdlcorchestrator.requirement.domain.RequirementAnalysis;
import com.vasanth.agenticsdlcorchestrator.requirement.domain.RequirementAnalysis.AcceptanceCriterion;
import com.vasanth.agenticsdlcorchestrator.requirement.domain.RequirementAnalysis.AmbiguityAnalysis;
import com.vasanth.agenticsdlcorchestrator.requirement.domain.RequirementAnalysis.RiskLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DynamicTaskPlannerTest {
    private final DynamicTaskPlanner planner = new DynamicTaskPlanner();
    private final PlanGraphValidator validator = new PlanGraphValidator();

    @Test
    void generatesDifferentConcreteGraphsFromRequirementAndRepositoryEvidence() {
        var aliasPlan = planner.plan(requirement("Reserve custom aliases and redirect through them", "AC-ALIAS"),
                repository(Map.of("AC-ALIAS", List.of("src/AliasController.java", "src/ShortUrlService.java"))));
        var analyticsPlan = planner.plan(requirement("Record redirects and return UTC daily analytics", "AC-ANALYTICS"),
                repository(Map.of("AC-ANALYTICS", List.of("src/RedirectService.java", "db/V2__events.sql"))));

        assertThat(aliasPlan.tasks()).extracting(PlannedTask::id).anyMatch(id -> id.contains("custom-alias"));
        assertThat(analyticsPlan.tasks()).extracting(PlannedTask::id).anyMatch(id -> id.contains("redirect-analytics"));
        assertThat(aliasPlan.tasks()).extracting(PlannedTask::id)
                .isNotEqualTo(analyticsPlan.tasks().stream().map(PlannedTask::id).toList());
        assertThat(aliasPlan.tasks().stream().filter(task -> task.agentRole().equals("IMPLEMENTATION")).findFirst().orElseThrow()
                .impactedComponents()).contains("src/AliasController.java");
        assertThat(aliasPlan.executionLayers().values()).anyMatch(layer ->
                layer.stream().anyMatch(id -> id.startsWith("risk-review"))
                        || layer.stream().anyMatch(id -> id.startsWith("documentation")));
    }

    @Test
    void rejectsDuplicateUnknownMissingCyclicUnreachableAndIncompletePlans() {
        EngineeringTaskPlan valid = planner.plan(requirement("Create a short URL and return HTTP 201", "AC-1"),
                repository(Map.of("AC-1", List.of())));
        PlannedTask first = valid.tasks().getFirst();
        List<PlannedTask> duplicate = new ArrayList<>(valid.tasks());
        duplicate.add(first);
        assertInvalid(valid, duplicate, "duplicate task id");

        replaceAndAssert(valid, 0, copy(first, "repository-analysis", "UNKNOWN", List.of()), "unknown agent role");
        replaceAndAssert(valid, 1, copy(valid.tasks().get(1), "architecture", "ARCHITECTURE", List.of("missing")), "missing dependency");
        replaceAndAssert(valid, 0, copy(first, "repository-analysis", "REPOSITORY", List.of("architecture")), "cycle detected");
        PlannedTask orphan = new PlannedTask("orphan", "orphan", "TEST", List.of(), List.of("AC-1"), List.of(), "READY", "TESTED");
        List<PlannedTask> unreachable = new ArrayList<>(valid.tasks());
        unreachable.add(orphan);
        assertInvalid(valid, unreachable, "unreachable tasks");

        List<PlannedTask> noRisk = valid.tasks().stream().filter(task -> !task.agentRole().equals("RISK")).toList();
        assertInvalid(valid, noRisk, "missing dependency");
    }

    private void replaceAndAssert(EngineeringTaskPlan valid, int index, PlannedTask replacement, String message) {
        List<PlannedTask> changed = new ArrayList<>(valid.tasks());
        changed.set(index, replacement);
        assertInvalid(valid, changed, message);
    }

    private void assertInvalid(EngineeringTaskPlan original, List<PlannedTask> tasks, String message) {
        assertThatThrownBy(() -> validator.validate(new EngineeringTaskPlan(
                original.requirementHash(), tasks, original.executionLayers())))
                .isInstanceOf(InvalidPlanException.class).hasMessageContaining(message);
    }

    private PlannedTask copy(PlannedTask task, String id, String role, List<String> dependencies) {
        return new PlannedTask(id, task.title(), role, dependencies, task.acceptanceCriterionIds(),
                task.impactedComponents(), task.entryGate(), task.exitGate());
    }

    private RequirementAnalysis requirement(String text, String criterionId) {
        return new RequirementAnalysis(text,
                List.of(new AcceptanceCriterion(criterionId, text, true)), List.of(), List.of(), List.of(),
                new AmbiguityAnalysis(false, RiskLevel.LOW, List.of(), List.of()));
    }

    private RepositoryMap repository(Map<String, List<String>> impacts) {
        return new RepositoryMap(List.of("."), List.of("example"), List.of(),
                List.of("src/Controller.java"), List.of("src/Service.java"), List.of(),
                List.of("src/Repository.java"), List.of(), List.of(), List.of(),
                List.of("controller -> service -> repository"), List.of("Maven"), impacts);
    }
}
