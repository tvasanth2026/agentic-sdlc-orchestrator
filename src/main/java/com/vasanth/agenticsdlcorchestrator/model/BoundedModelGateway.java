package com.vasanth.agenticsdlcorchestrator.model;

import com.vasanth.agenticsdlcorchestrator.agent.SpecialistAgentModels.SpecialistOutput;
import com.vasanth.agenticsdlcorchestrator.config.ModelProviderProperties;
import com.vasanth.agenticsdlcorchestrator.execution.ModelProvider;
import com.vasanth.agenticsdlcorchestrator.execution.model.ExecutionModels.ModelRequest;
import com.vasanth.agenticsdlcorchestrator.execution.model.ExecutionModels.ModelResponse;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.vasanth.agenticsdlcorchestrator.observability.PlatformMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

public final class BoundedModelGateway {
    private final ModelProvider provider;
    private final ModelProviderProperties properties;
    private final ObjectMapper objectMapper;
    private final SecretRedactor redactor = new SecretRedactor();
    private final SpecialistOutputValidator validator = new SpecialistOutputValidator();
    private final PlatformMetrics metrics;

    public BoundedModelGateway(ModelProvider provider, ModelProviderProperties properties, ObjectMapper objectMapper) {
        this(provider, properties, objectMapper, new PlatformMetrics(new SimpleMeterRegistry()));
    }

    public BoundedModelGateway(ModelProvider provider, ModelProviderProperties properties, ObjectMapper objectMapper,
                               PlatformMetrics metrics) {
        this.provider = provider;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    public ValidatedModelOutput generate(ModelRequest request) {
        ModelResponse response = generateRaw(request);
        SpecialistOutput output = validator.validate(deserializeOutput(response.structuredOutput()));
        return new ValidatedModelOutput(response, output);
    }

    public ModelResponse generateRaw(ModelRequest request) {
        String serializedContext = serialize(request.context());
        if (serializedContext.length() > properties.maxContextCharacters()) {
            throw new ModelBoundaryException("model context exceeds configured character limit");
        }
        ModelRequest safeRequest = new ModelRequest(request.agentRole(), request.schemaName(),
                redactor.redact(request.instructions()), deserializeMap(redactor.redact(serializedContext)),
                Math.min(request.maxOutputCharacters(), properties.maxOutputCharacters()));
        ModelResponse response = invokeWithTimeout(safeRequest);
        if (response.structuredOutput() == null
                || response.structuredOutput().length() > safeRequest.maxOutputCharacters()) {
            throw new ModelBoundaryException("model output exceeds configured character limit");
        }
        return response;
    }

    private ModelResponse invokeWithTimeout(ModelRequest request) {
        var sample = metrics.modelStarted();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<ModelResponse> future = executor.submit(() -> provider.generate(request));
            try {
                ModelResponse response = future.get(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
                metrics.modelFinished(sample, response.provider(), "success");
                return response;
            } catch (TimeoutException exception) {
                future.cancel(true);
                throw new ModelBoundaryException("model call timed out", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ModelBoundaryException("model call interrupted", exception);
            } catch (ExecutionException exception) {
                throw new ModelBoundaryException("model provider failed", exception.getCause());
            }
        } catch (RuntimeException exception) {
            metrics.modelFinished(sample, properties.provider(), "failure");
            throw exception;
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new ModelBoundaryException("model context serialization failed", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> deserializeMap(String value) {
        try {
            return objectMapper.readValue(value, java.util.Map.class);
        } catch (JacksonException exception) {
            throw new ModelBoundaryException("redacted context is invalid", exception);
        }
    }

    private SpecialistOutput deserializeOutput(String value) {
        try {
            return objectMapper.readValue(value, SpecialistOutput.class);
        } catch (JacksonException exception) {
            throw new ModelBoundaryException("model output does not match specialist-output schema", exception);
        }
    }

    public record ValidatedModelOutput(ModelResponse response, SpecialistOutput output) {}
}
