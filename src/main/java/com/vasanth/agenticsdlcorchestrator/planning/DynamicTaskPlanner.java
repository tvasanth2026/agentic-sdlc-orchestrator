package com.vasanth.agenticsdlcorchestrator.planning;

import com.vasanth.agenticsdlcorchestrator.planning.domain.PlanModels.EngineeringTaskPlan;
import com.vasanth.agenticsdlcorchestrator.planning.domain.PlanModels.PlannedTask;
import com.vasanth.agenticsdlcorchestrator.repository.domain.RepositoryModels.RepositoryMap;
import com.vasanth.agenticsdlcorchestrator.requirement.domain.RequirementAnalysis;
import com.vasanth.agenticsdlcorchestrator.requirement.domain.RequirementAnalysis.AcceptanceCriterion;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DynamicTaskPlanner {
    public EngineeringTaskPlan plan(RequirementAnalysis requirement, RepositoryMap repository) {
        List<PlannedTask> tasks = new ArrayList<>();
        tasks.add(task("repository-analysis", "Confirm repository impact map", "REPOSITORY", List.of(),
                List.of(), allComponents(repository), "REQUIREMENT_VALID", "REPOSITORY_MAP_VALID"));
        tasks.add(task("architecture", "Design changes for " + summarize(requirement.normalizedProblem()),
                "ARCHITECTURE", List.of("repository-analysis"), criterionIds(requirement), allComponents(repository),
                "REPOSITORY_MAP_VALID", "ARCHITECTURE_VALID"));
        tasks.add(task("change-approval", "Approve current engineering plan", "CHANGE_APPROVER",
                List.of("architecture"), criterionIds(requirement), List.of(),
                "PLAN_HASH_CURRENT", "CHANGE_APPROVED"));

        List<String> testTasks = new ArrayList<>();
        for (AcceptanceCriterion criterion : requirement.acceptanceCriteria().stream()
                .filter(AcceptanceCriterion::behavioral).toList()) {
            String capability = capability(criterion.description(), criterion.id());
            String criterionSuffix = criterion.id().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
            String implementationId = "implement-" + capability + "-" + criterionSuffix;
            String testId = "test-" + capability + "-" + criterionSuffix;
            List<String> impacts = repository.acceptanceCriterionImpacts()
                    .getOrDefault(criterion.id(), List.of());
            tasks.add(task(implementationId, "Implement " + capability.replace('-', ' '), "IMPLEMENTATION",
                    List.of("change-approval"), List.of(criterion.id()), impacts,
                    "CHANGE_APPROVED", "PRODUCTION_PROPOSAL_VALID"));
            tasks.add(task(testId, "Generate behavioral tests for " + capability.replace('-', ' '), "TEST",
                    List.of(implementationId), List.of(criterion.id()), impacts,
                    "PRODUCTION_PROPOSAL_VALID", "TEST_PROPOSAL_VALID"));
            testTasks.add(testId);
        }
        if (testTasks.isEmpty()) throw new IllegalArgumentException("plan requires a behavioral acceptance criterion");
        tasks.add(task("validation", "Compile and execute generated tests", "VALIDATION", testTasks,
                criterionIds(requirement), allComponents(repository), "PROPOSALS_APPLIED", "BUILD_AND_TEST_PASSED"));
        tasks.add(task("risk-review", "Review security, operational, and change risks", "RISK",
                List.of("validation"), criterionIds(requirement), allComponents(repository),
                "VALIDATION_EVIDENCE_CURRENT", "RISK_REVIEW_PASSED"));
        tasks.add(task("documentation", "Generate current-revision engineering documentation", "DOCUMENTATION",
                List.of("validation"), criterionIds(requirement), allComponents(repository),
                "VALIDATION_EVIDENCE_CURRENT", "DOCUMENTATION_VALID"));
        tasks.add(task("release-readiness", "Validate feature completion and release readiness", "RELEASE_READINESS",
                List.of("validation", "risk-review", "documentation"), criterionIds(requirement), List.of(),
                "TRACEABILITY_COMPLETE", "RELEASE_OUTCOME_HASHED"));
        tasks.add(task("release-approval", "Approve exact current engineering outcome", "RELEASE_APPROVER",
                List.of("release-readiness"), criterionIds(requirement), List.of(),
                "OUTCOME_HASH_CURRENT", "RELEASE_APPROVED"));

        EngineeringTaskPlan candidate = new EngineeringTaskPlan(sha256(requirement.normalizedProblem()), tasks,
                executionLayers(tasks));
        return new PlanGraphValidator().validate(candidate);
    }

    private PlannedTask task(String id, String title, String role, List<String> dependencies,
                             List<String> criteria, List<String> impacts, String entry, String exit) {
        return new PlannedTask(id, title, role, dependencies, criteria, impacts, entry, exit);
    }

    private List<String> criterionIds(RequirementAnalysis requirement) {
        return requirement.acceptanceCriteria().stream().map(AcceptanceCriterion::id).toList();
    }

    private List<String> allComponents(RepositoryMap repository) {
        List<String> result = new ArrayList<>();
        result.addAll(repository.controllers());
        result.addAll(repository.services());
        result.addAll(repository.domainObjects());
        result.addAll(repository.repositories());
        return result.stream().distinct().limit(100).toList();
    }

    private String capability(String description, String fallback) {
        String lower = description.toLowerCase(Locale.ROOT);
        if (lower.contains("alias")) return "custom-alias";
        if (lower.contains("analytic") || lower.contains("count")) return "redirect-analytics";
        if (lower.contains("expir") || lower.contains("ttl")) return "url-expiry";
        if (lower.contains("deactiv")) return "url-deactivation";
        return "criterion-" + fallback.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    }

    private String summarize(String value) {
        return value.length() <= 80 ? value : value.substring(0, 80);
    }

    private Map<String, List<String>> executionLayers(List<PlannedTask> tasks) {
        Map<String, Integer> depths = new LinkedHashMap<>();
        Map<String, PlannedTask> byId = new LinkedHashMap<>();
        tasks.forEach(task -> byId.put(task.id(), task));
        for (PlannedTask task : tasks) depth(task.id(), byId, depths);
        Map<String, List<String>> result = new LinkedHashMap<>();
        depths.forEach((id, depth) -> result.computeIfAbsent("layer-" + depth, ignored -> new ArrayList<>()).add(id));
        return result;
    }

    private int depth(String id, Map<String, PlannedTask> byId, Map<String, Integer> memo) {
        if (memo.containsKey(id)) return memo.get(id);
        PlannedTask task = byId.get(id);
        int value = task.dependencies().isEmpty() ? 0
                : 1 + task.dependencies().stream().mapToInt(dependency -> depth(dependency, byId, memo)).max().orElse(0);
        memo.put(id, value);
        return value;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
