package com.vasanth.agenticsdlcorchestrator.model;

import com.vasanth.agenticsdlcorchestrator.config.ModelProviderProperties;
import com.vasanth.agenticsdlcorchestrator.execution.ModelProvider;
import com.vasanth.agenticsdlcorchestrator.execution.model.ExecutionModels.ModelRequest;
import com.vasanth.agenticsdlcorchestrator.execution.model.ExecutionModels.ModelResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public final class OpenAiResponsesModelProvider implements ModelProvider {
    private final ModelProviderProperties properties;
    private final OpenAiTransport transport;
    private final ObjectMapper objectMapper;

    public OpenAiResponsesModelProvider(ModelProviderProperties properties, OpenAiTransport transport,
                                        ObjectMapper objectMapper) {
        this.properties = properties;
        this.transport = transport;
        this.objectMapper = objectMapper;
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalArgumentException("OPENAI_API_KEY is required when agentic.model.provider=openai");
        }
    }

    @Override
    public ModelResponse generate(ModelRequest request) {
        Instant started = Instant.now();
        String body = serialize(Map.of(
                "model", properties.model(),
                "instructions", request.instructions(),
                "input", serialize(request.context()),
                "max_output_tokens", Math.max(256, request.maxOutputCharacters() / 4),
                "text", Map.of("format", Map.of(
                        "type", "json_schema",
                        "name", request.schemaName(),
                        "strict", true,
                        "schema", schema(request.schemaName())))));
        String raw = transport.post(properties.baseUrl().toString(), properties.apiKey(), body, properties.timeout());
        String output = extractOutputText(raw);
        return new ModelResponse("openai", properties.model(), output, Duration.between(started, Instant.now()));
    }

    @SuppressWarnings("unchecked")
    private String extractOutputText(String raw) {
        try {
            Map<String, Object> response = objectMapper.readValue(raw, Map.class);
            Object direct = response.get("output_text");
            if (direct instanceof String text && !text.isBlank()) return text;
            for (Object item : (List<Object>) response.getOrDefault("output", List.of())) {
                if (!(item instanceof Map<?, ?> outputItem)) continue;
                Object rawContent = outputItem.get("content");
                if (!(rawContent instanceof List<?> contentParts)) continue;
                for (Object part : contentParts) {
                    if (part instanceof Map<?, ?> content && content.get("text") instanceof String text && !text.isBlank()) {
                        return text;
                    }
                }
            }
            throw new ModelBoundaryException("OpenAI response contains no structured output text");
        } catch (JacksonException | ClassCastException exception) {
            throw new ModelBoundaryException("OpenAI response shape is invalid", exception);
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new ModelBoundaryException("OpenAI request serialization failed", exception);
        }
    }

    private Map<String, Object> schema(String schemaName) {
        return switch (schemaName) {
            case "specialist_output" -> SpecialistJsonSchema.VALUE;
            case "file_operation_proposal" -> PatchJsonSchema.VALUE;
            default -> throw new ModelBoundaryException("unsupported model schema: " + schemaName);
        };
    }
}
