package com.vasanth.agenticsdlcorchestrator.agent;

import com.vasanth.agenticsdlcorchestrator.agent.SpecialistAgentModels.SpecialistAgentInput;
import com.vasanth.agenticsdlcorchestrator.agent.SpecialistAgentModels.SpecialistAgentResult;
import com.vasanth.agenticsdlcorchestrator.execution.model.ExecutionModels.ModelRequest;
import com.vasanth.agenticsdlcorchestrator.model.BoundedModelGateway;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public final class ModelBackedSpecialistAgent implements SpecialistAgent {
    private final SpecialistAgentRole role;
    private final BoundedModelGateway gateway;
    private final ObjectMapper objectMapper;
    private final int maxOutputCharacters;

    public ModelBackedSpecialistAgent(SpecialistAgentRole role, BoundedModelGateway gateway,
                                      ObjectMapper objectMapper, int maxOutputCharacters) {
        this.role = role;
        this.gateway = gateway;
        this.objectMapper = objectMapper;
        this.maxOutputCharacters = maxOutputCharacters;
    }

    @Override
    public SpecialistAgentRole role() {
        return role;
    }

    @Override
    public SpecialistAgentResult execute(SpecialistAgentInput input) {
        Map<String, Object> context = new LinkedHashMap<>(input.boundedContext());
        context.put("objective", input.objective());
        context.put("inputArtifactHashes", input.inputArtifactHashes());
        var validated = gateway.generate(new ModelRequest(role.name(), "specialist_output",
                instructions(role), context, maxOutputCharacters));
        String json = serialize(validated.output());
        return new SpecialistAgentResult(role, validated.response().provider(), validated.response().model(),
                validated.output(), json, sha256(json), validated.response().duration());
    }

    private String instructions(SpecialistAgentRole role) {
        return "Act as the " + role.name().toLowerCase().replace('_', ' ')
                + " specialist. Use only supplied current-revision evidence. Return strict JSON matching the schema. "
                + "Never claim source application, validation, repair, or release readiness without its evidence.";
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("specialist output serialization failed", exception);
        }
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
