package com.vasanth.agenticsdlcorchestrator.model;

import com.vasanth.agenticsdlcorchestrator.agent.SpecialistAgentModels.SpecialistOutput;
import com.vasanth.agenticsdlcorchestrator.execution.ModelProvider;
import com.vasanth.agenticsdlcorchestrator.execution.model.ExecutionModels.ModelRequest;
import com.vasanth.agenticsdlcorchestrator.execution.model.ExecutionModels.ModelResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public final class DeterministicModelProvider implements ModelProvider {
    private final ObjectMapper objectMapper;

    public DeterministicModelProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ModelResponse generate(ModelRequest request) {
        Instant started = Instant.now();
        if (request.schemaName().equals("file_operation_proposal")) {
            return new ModelResponse("deterministic", "deterministic-v1",
                    serialize(request.context().get("proposedOperations")), Duration.between(started, Instant.now()));
        }
        String objective = String.valueOf(request.context().getOrDefault("objective", "the current revision"));
        boolean evidenceReady = Boolean.TRUE.equals(request.context().get("validationPassed"));
        boolean lateStage = request.agentRole().equals("RELEASE_READINESS");
        boolean failureDependent = request.agentRole().equals("VALIDATION_DIAGNOSIS")
                || request.agentRole().equals("REPAIR");
        SpecialistOutput output = new SpecialistOutput(
                request.agentRole() + " evaluated " + objective,
                decision(request.agentRole(), evidenceReady),
                List.of("Inputs are limited to current-revision persisted evidence."),
                List.of("Downstream claims remain provisional until source application and real validation complete."),
                List.of(deliverable(request.agentRole(), objective)),
                !(lateStage || failureDependent) || evidenceReady);
        return new ModelResponse("deterministic", "deterministic-v1",
                serialize(output), Duration.between(started, Instant.now()));
    }

    private List<String> decision(String role, boolean validated) {
        return switch (role) {
            case "RELEASE_READINESS" -> List.of(validated
                    ? "Current evidence may proceed to release-readiness validation."
                    : "Release readiness is blocked until generated changes pass real validation.");
            case "VALIDATION_DIAGNOSIS", "REPAIR" -> List.of(validated
                    ? "Use current validation evidence."
                    : "No validation failure evidence exists; do not invent a repair.");
            default -> List.of("Produce a current-revision " + role.toLowerCase().replace('_', ' ') + " artifact.");
        };
    }

    private String deliverable(String role, String objective) {
        return role.toLowerCase().replace('_', '-') + ": " + objective;
    }

    private String serialize(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JacksonException exception) {
            throw new ModelBoundaryException("deterministic output serialization failed", exception);
        }
    }
}
