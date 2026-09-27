package com.vasanth.agenticsdlcorchestrator.agent;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class SpecialistAgentModels {
    private SpecialistAgentModels() {}

    public record SpecialistAgentInput(String objective, Map<String, Object> boundedContext,
                                       List<String> inputArtifactHashes) {
        public SpecialistAgentInput {
            boundedContext = Map.copyOf(boundedContext);
            inputArtifactHashes = List.copyOf(inputArtifactHashes);
        }
    }

    public record SpecialistOutput(String summary, List<String> decisions, List<String> assumptions,
                                   List<String> risks, List<String> deliverables, boolean ready) {
        public SpecialistOutput {
            decisions = List.copyOf(decisions);
            assumptions = List.copyOf(assumptions);
            risks = List.copyOf(risks);
            deliverables = List.copyOf(deliverables);
        }
    }

    public record SpecialistAgentResult(SpecialistAgentRole role, String provider, String model,
                                        SpecialistOutput output, String outputJson, String outputHash,
                                        Duration duration) {}
}
