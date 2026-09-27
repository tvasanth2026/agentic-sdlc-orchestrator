package com.vasanth.agenticsdlcorchestrator.requirement.agent;

import com.vasanth.agenticsdlcorchestrator.requirement.domain.RequirementAnalysis;
import com.vasanth.agenticsdlcorchestrator.requirement.domain.RequirementAnalysis.AcceptanceCriterion;
import com.vasanth.agenticsdlcorchestrator.requirement.domain.RequirementAnalysis.AmbiguityAnalysis;
import com.vasanth.agenticsdlcorchestrator.requirement.domain.RequirementAnalysis.ClarificationQuestion;
import com.vasanth.agenticsdlcorchestrator.requirement.domain.RequirementAnalysis.RiskLevel;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class DeterministicRequirementInterpreterAgent implements RequirementInterpreterAgent {
    private static final Pattern SPACE = Pattern.compile("\\s+");
    private static final Pattern OBSERVABLE = Pattern.compile(
            "\\b(return|respond|redirect|reject|persist|record|create|update|delete|expire|deactivate|display|emit)\\w*\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern UNRESOLVED_CHOICE = Pattern.compile(
            "\\b(either|maybe|optionally|as appropriate|as needed|or something|etc\\.)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MEASURABLE = Pattern.compile(
            "(\\b\\d+\\b|\\bhttp\\s*\\d{3}\\b|\\biso[- ]?8601\\b|\\butc\\b|\\bmilliseconds?\\b|\\bseconds?\\b|\\bminutes?\\b|\\bhours?\\b|\\bdays?\\b)",
            Pattern.CASE_INSENSITIVE);

    @Override
    public RequirementAnalysis interpret(String rawRequirement, String repositoryPath) {
        String requirement = normalize(rawRequirement);
        String lower = requirement.toLowerCase(Locale.ROOT);
        List<ClarificationQuestion> questions = new ArrayList<>();
        List<String> reasons = new ArrayList<>();

        if (requirement.length() < 45) {
            add(questions, reasons, "scope", "What exact user-visible behavior and API surface must change?",
                    "The requirement does not define enough behavioral scope.");
        }
        if (!OBSERVABLE.matcher(requirement).find()) {
            add(questions, reasons, "outcome", "What observable response or state change proves this requirement is complete?",
                    "No observable outcome is defined.");
        }
        if (UNRESOLVED_CHOICE.matcher(requirement).find()) {
            add(questions, reasons, "decision", "Which of the unresolved alternatives should be implemented?",
                    "The requirement contains an unresolved implementation or behavior choice.");
        }
        if (mentions(lower, "expir", "ttl") && !MEASURABLE.matcher(requirement).find()) {
            add(questions, reasons, "expiry", "Who selects the expiry, what time format is used, and what response follows expiry?",
                    "Expiry semantics lack a duration/time format or observable expired response.");
        }
        if (mentions(lower, "analytic", "count", "report") && !mentions(lower, "utc", "timezone", "time zone")) {
            add(questions, reasons, "time-boundary", "Which time zone and day boundary govern analytics aggregation?",
                    "Time-based analytics do not define a time zone.");
        }
        if (mentions(lower, "alias") && !mentions(lower, "case-sensitive", "case insensitive", "case-insensitive")) {
            add(questions, reasons, "alias-case", "Are custom aliases case-sensitive, and how are duplicates handled?",
                    "Alias identity and duplicate semantics are unspecified.");
        }

        questions = uniqueQuestions(questions);
        boolean ambiguous = !questions.isEmpty();
        RiskLevel risk = questions.size() >= 3 ? RiskLevel.HIGH : ambiguous ? RiskLevel.MEDIUM : RiskLevel.LOW;
        List<AcceptanceCriterion> criteria = deriveCriteria(requirement, lower);
        List<String> assumptions = List.of("Changes remain inside the approved repository workspace.",
                "Existing behavior remains compatible unless an acceptance criterion explicitly changes it.");
        List<String> constraints = List.of("Production and test changes require validation before task completion.",
                "No source mutation is permitted while clarification is required.",
                "Repository: " + repositoryPath);
        List<String> risks = ambiguous
                ? List.of("Implementing before clarification could encode incorrect behavior.",
                          "Downstream requirement-derived artifacts must be invalidated after clarification.")
                : List.of("Generated tests must prove the requested behavior through the runtime path.");
        return new RequirementAnalysis(requirement, criteria, assumptions, constraints, risks,
                new AmbiguityAnalysis(ambiguous, risk, questions, reasons));
    }

    private List<AcceptanceCriterion> deriveCriteria(String requirement, String lower) {
        List<AcceptanceCriterion> criteria = new ArrayList<>();
        criteria.add(new AcceptanceCriterion("AC-1",
                "The target repository implements the requested behavior: " + requirement, true));
        criteria.add(new AcceptanceCriterion("AC-2",
                "Automated tests exercise the requested behavior through a production runtime path.", true));
        int nextId = 3;
        if (mentions(lower, "expir", "ttl")) {
            criteria.add(new AcceptanceCriterion("AC-" + nextId++,
                    "Expired resources follow the clarified expiry response contract.", true));
        }
        if (mentions(lower, "analytic", "count", "report")) {
            criteria.add(new AcceptanceCriterion("AC-" + nextId++,
                    "Analytics totals and time-bucket results are derived from real runtime events.", true));
        }
        if (mentions(lower, "alias")) {
            criteria.add(new AcceptanceCriterion("AC-" + nextId,
                    "Alias validation, reservation, duplicate handling, and redirect behavior are tested.", true));
        }
        return List.copyOf(criteria);
    }

    private void add(List<ClarificationQuestion> questions, List<String> reasons, String dimension,
                     String prompt, String reason) {
        questions.add(new ClarificationQuestion("CQ-" + (questions.size() + 1), dimension, prompt));
        reasons.add(reason);
    }

    private List<ClarificationQuestion> uniqueQuestions(List<ClarificationQuestion> source) {
        Set<String> dimensions = new LinkedHashSet<>();
        List<ClarificationQuestion> result = new ArrayList<>();
        for (ClarificationQuestion question : source) {
            if (dimensions.add(question.dimension())) {
                result.add(new ClarificationQuestion("CQ-" + (result.size() + 1), question.dimension(), question.prompt()));
            }
        }
        return List.copyOf(result);
    }

    private boolean mentions(String value, String... fragments) {
        for (String fragment : fragments) if (value.contains(fragment)) return true;
        return false;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("requirement is required");
        return SPACE.matcher(value.trim()).replaceAll(" ");
    }
}
