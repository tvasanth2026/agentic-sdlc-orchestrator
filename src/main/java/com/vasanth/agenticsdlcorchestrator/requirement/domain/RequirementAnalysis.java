package com.vasanth.agenticsdlcorchestrator.requirement.domain;

import java.util.List;

public record RequirementAnalysis(
        String normalizedProblem,
        List<AcceptanceCriterion> acceptanceCriteria,
        List<String> assumptions,
        List<String> constraints,
        List<String> risks,
        AmbiguityAnalysis ambiguity) {

    public RequirementAnalysis {
        acceptanceCriteria = List.copyOf(acceptanceCriteria);
        assumptions = List.copyOf(assumptions);
        constraints = List.copyOf(constraints);
        risks = List.copyOf(risks);
    }

    public record AcceptanceCriterion(String id, String description, boolean behavioral) {}
    public record AmbiguityAnalysis(boolean clarificationRequired, RiskLevel riskLevel,
                                    List<ClarificationQuestion> questions, List<String> reasons) {
        public AmbiguityAnalysis {
            questions = List.copyOf(questions);
            reasons = List.copyOf(reasons);
        }
    }
    public record ClarificationQuestion(String id, String dimension, String prompt) {}
    public enum RiskLevel { LOW, MEDIUM, HIGH }
}

