package com.vasanth.agenticsdlcorchestrator.model;

import com.vasanth.agenticsdlcorchestrator.agent.SpecialistAgentModels.SpecialistOutput;
import java.util.List;

public final class SpecialistOutputValidator {
    public SpecialistOutput validate(SpecialistOutput output) {
        if (output == null) throw new ModelBoundaryException("model output is required");
        requireText(output.summary(), "summary", 4_000);
        requireList(output.decisions(), "decisions", 50);
        requireList(output.assumptions(), "assumptions", 50);
        requireList(output.risks(), "risks", 50);
        requireList(output.deliverables(), "deliverables", 100);
        return output;
    }

    private void requireList(List<String> values, String name, int maximum) {
        if (values == null || values.size() > maximum) {
            throw new ModelBoundaryException(name + " must contain at most " + maximum + " items");
        }
        values.forEach(value -> requireText(value, name, 4_000));
    }

    private void requireText(String value, String name, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new ModelBoundaryException(name + " is blank or exceeds " + maximum + " characters");
        }
    }
}
