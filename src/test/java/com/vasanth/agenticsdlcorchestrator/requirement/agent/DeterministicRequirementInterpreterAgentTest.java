package com.vasanth.agenticsdlcorchestrator.requirement.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vasanth.agenticsdlcorchestrator.requirement.domain.RequirementAnalysis;
import com.vasanth.agenticsdlcorchestrator.requirement.domain.RequirementAnalysis.AmbiguityAnalysis;
import com.vasanth.agenticsdlcorchestrator.requirement.domain.RequirementAnalysis.RiskLevel;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeterministicRequirementInterpreterAgentTest {
    private final DeterministicRequirementInterpreterAgent agent = new DeterministicRequirementInterpreterAgent();
    private final RequirementAnalysisValidator validator = new RequirementAnalysisValidator();

    @Test
    void usesStructuralAndDomainDimensionsRatherThanOneTriggerPhrase() {
        var underspecified = validator.validate(agent.interpret(
                "Please make links better for customers", "repo"));
        var unresolvedAnalytics = validator.validate(agent.interpret(
                "Record redirect analytics and return a daily report from GET /analytics", "repo"));
        var clear = validator.validate(agent.interpret(
                "Record redirects and return total and UTC daily counts from GET /analytics with HTTP 200.", "repo"));

        assertThat(underspecified.ambiguity().clarificationRequired()).isTrue();
        assertThat(underspecified.ambiguity().questions()).extracting("dimension").contains("outcome");
        assertThat(unresolvedAnalytics.ambiguity().questions()).extracting("dimension").contains("time-boundary");
        assertThat(clear.ambiguity().clarificationRequired()).isFalse();
        assertThat(underspecified.acceptanceCriteria().getFirst().description())
                .isNotEqualTo(clear.acceptanceCriteria().getFirst().description());
    }

    @Test
    void validatorRejectsContradictoryAgentOutput() {
        RequirementAnalysis invalid = new RequirementAnalysis("Normalized", List.of(
                new RequirementAnalysis.AcceptanceCriterion("AC-1", "Behavior", true)),
                List.of(), List.of(), List.of(),
                new AmbiguityAnalysis(true, RiskLevel.MEDIUM, List.of(), List.of("missing detail")));

        assertThatThrownBy(() -> validator.validate(invalid))
                .hasMessageContaining("requires clarification questions");
    }

    @Test
    void assignsUniqueCriteriaAcrossMultipleRequirementDimensions() {
        var result = validator.validate(agent.interpret(
                "Create aliases, record redirect analytics with UTC daily counts, and expire links after 7 days; return HTTP 410 after expiry and treat aliases as case-sensitive.",
                "repo"));

        assertThat(result.acceptanceCriteria()).extracting("id").doesNotHaveDuplicates();
        assertThat(result.acceptanceCriteria()).hasSizeGreaterThanOrEqualTo(5);
    }
}
