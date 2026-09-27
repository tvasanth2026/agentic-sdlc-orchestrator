package com.vasanth.agenticsdlcorchestrator.requirement.agent;

import com.vasanth.agenticsdlcorchestrator.requirement.domain.RequirementAnalysis;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class RequirementAnalysisValidator {
    public RequirementAnalysis validate(RequirementAnalysis analysis) {
        if (analysis == null || analysis.normalizedProblem() == null || analysis.normalizedProblem().isBlank()) {
            throw new IllegalArgumentException("normalized problem is required");
        }
        if (analysis.acceptanceCriteria().isEmpty()) throw new IllegalArgumentException("acceptance criteria are required");
        Set<String> criterionIds = new HashSet<>();
        analysis.acceptanceCriteria().forEach(criterion -> {
            if (!criterionIds.add(criterion.id())) throw new IllegalArgumentException("duplicate acceptance criterion id");
            if (criterion.description() == null || criterion.description().isBlank()) {
                throw new IllegalArgumentException("acceptance criterion description is required");
            }
        });
        if (analysis.ambiguity().clarificationRequired() && analysis.ambiguity().questions().isEmpty()) {
            throw new IllegalArgumentException("ambiguous analysis requires clarification questions");
        }
        if (!analysis.ambiguity().clarificationRequired() && !analysis.ambiguity().questions().isEmpty()) {
            throw new IllegalArgumentException("clear analysis cannot contain clarification questions");
        }
        return analysis;
    }
}
