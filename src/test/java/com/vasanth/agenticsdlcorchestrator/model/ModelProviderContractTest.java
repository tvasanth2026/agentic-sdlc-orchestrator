package com.vasanth.agenticsdlcorchestrator.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vasanth.agenticsdlcorchestrator.agent.SpecialistAgentModels.SpecialistOutput;
import com.vasanth.agenticsdlcorchestrator.config.ModelProviderProperties;
import com.vasanth.agenticsdlcorchestrator.execution.ModelProvider;
import com.vasanth.agenticsdlcorchestrator.execution.model.ExecutionModels.ModelRequest;
import com.vasanth.agenticsdlcorchestrator.execution.model.ExecutionModels.ModelResponse;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ModelProviderContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deterministicProviderSatisfiesTheSharedStructuredContract() {
        var gateway = new BoundedModelGateway(new DeterministicModelProvider(objectMapper),
                properties(Duration.ofSeconds(1), 10_000, 5_000), objectMapper);

        var result = gateway.generate(request(Map.of("objective", "design URL expiry"), 5_000));

        assertThat(result.response().provider()).isEqualTo("deterministic");
        assertThat(result.output().summary()).contains("ARCHITECTURE", "design URL expiry");
        assertThat(result.output().deliverables()).isNotEmpty();
    }

    @Test
    void openAiProviderUsesResponsesJsonSchemaAndTheSameOutputContract() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        SpecialistOutput output = new SpecialistOutput("Architecture complete", List.of("Use service boundary"),
                List.of("Existing API remains stable"), List.of("Migration risk"), List.of("architecture.md"), true);
        String response = objectMapper.writeValueAsString(Map.of("output_text", objectMapper.writeValueAsString(output)));
        OpenAiTransport transport = (endpoint, apiKey, body, timeout) -> {
            requestBody.set(body);
            return response;
        };
        var provider = new OpenAiResponsesModelProvider(properties(Duration.ofSeconds(1), 10_000, 5_000),
                transport, objectMapper);
        var gateway = new BoundedModelGateway(provider, properties(Duration.ofSeconds(1), 10_000, 5_000), objectMapper);

        var result = gateway.generate(request(Map.of("objective", "design URL expiry"), 5_000));

        assertThat(result.response().provider()).isEqualTo("openai");
        assertThat(result.output()).isEqualTo(output);
        assertThat(requestBody.get()).contains("json_schema", "specialist_output", "strict");
    }

    @Test
    void openAiProviderUsesTheFileOperationSchemaForPatchAgents() {
        AtomicReference<String> requestBody = new AtomicReference<>();
        OpenAiTransport transport = (endpoint, apiKey, body, timeout) -> {
            requestBody.set(body);
            return "{\"output_text\":\"{\\\"operations\\\":[]}\"}";
        };
        var provider = new OpenAiResponsesModelProvider(properties(Duration.ofSeconds(1), 10_000, 5_000),
                transport, objectMapper);

        provider.generate(new ModelRequest("IMPLEMENTATION", "file_operation_proposal", "Return operations",
                Map.of("objective", "implement expiry"), 5_000));

        assertThat(requestBody.get()).contains("file_operation_proposal", "relativePath", "expectedSha256",
                "acceptanceCriterionIds", "CREATE", "UPDATE", "DELETE");
    }

    @Test
    void redactsSecretsBeforeCallingAnyProvider() {
        AtomicReference<ModelRequest> captured = new AtomicReference<>();
        ModelProvider provider = request -> {
            captured.set(request);
            return validResponse();
        };
        var gateway = new BoundedModelGateway(provider, properties(Duration.ofSeconds(1), 10_000, 5_000), objectMapper);

        gateway.generate(request(Map.of("token", "Bearer abcdefghijklmnop", "password", "password=hunter2"), 5_000));

        assertThat(captured.get().context().toString()).contains("REDACTED").doesNotContain("hunter2", "abcdefghijklmnop");
    }

    @Test
    void rejectsOversizedContextAndMalformedOutput() {
        var smallGateway = new BoundedModelGateway(request -> validResponse(),
                properties(Duration.ofSeconds(1), 1_024, 5_000), objectMapper);
        assertThatThrownBy(() -> smallGateway.generate(request(Map.of("content", "x".repeat(2_000)), 5_000)))
                .isInstanceOf(ModelBoundaryException.class).hasMessageContaining("context exceeds");

        var invalidGateway = new BoundedModelGateway(request -> new ModelResponse("mock", "mock", "{}",
                Duration.ZERO), properties(Duration.ofSeconds(1), 10_000, 5_000), objectMapper);
        assertThatThrownBy(() -> invalidGateway.generate(request(Map.of("objective", "test"), 5_000)))
                .isInstanceOf(ModelBoundaryException.class);

        var oversizedGateway = new BoundedModelGateway(request -> new ModelResponse("mock", "mock",
                "x".repeat(5_001), Duration.ZERO), properties(Duration.ofSeconds(1), 10_000, 5_000), objectMapper);
        assertThatThrownBy(() -> oversizedGateway.generate(request(Map.of("objective", "test"), 5_000)))
                .isInstanceOf(ModelBoundaryException.class).hasMessageContaining("output exceeds");
    }

    @Test
    void enforcesProviderTimeout() {
        ModelProvider slow = request -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return validResponse();
        };
        var gateway = new BoundedModelGateway(slow, properties(Duration.ofMillis(20), 10_000, 5_000), objectMapper);

        assertThatThrownBy(() -> gateway.generate(request(Map.of("objective", "test"), 5_000)))
                .isInstanceOf(ModelBoundaryException.class).hasMessageContaining("timed out");
    }

    private ModelRequest request(Map<String, Object> context, int maxOutput) {
        return new ModelRequest("ARCHITECTURE", "specialist_output", "Return strict output", context, maxOutput);
    }

    private ModelResponse validResponse() {
        try {
            String json = objectMapper.writeValueAsString(new SpecialistOutput("Valid output", List.of("Decision"),
                    List.of("Assumption"), List.of("Risk"), List.of("Artifact"), true));
            return new ModelResponse("mock", "mock-v1", json, Duration.ZERO);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private ModelProviderProperties properties(Duration timeout, int contextLimit, int outputLimit) {
        return new ModelProviderProperties("openai", "gpt-5", URI.create("https://api.openai.com/v1/responses"),
                "test-key", timeout, contextLimit, outputLimit);
    }
}
