package com.vasanth.agenticsdlcorchestrator.config;

import com.vasanth.agenticsdlcorchestrator.execution.ModelProvider;
import com.vasanth.agenticsdlcorchestrator.model.BoundedModelGateway;
import com.vasanth.agenticsdlcorchestrator.model.DeterministicModelProvider;
import com.vasanth.agenticsdlcorchestrator.model.JdkOpenAiTransport;
import com.vasanth.agenticsdlcorchestrator.model.OpenAiResponsesModelProvider;
import com.vasanth.agenticsdlcorchestrator.patch.FileOperationProposalAgent;
import com.vasanth.agenticsdlcorchestrator.patch.ModelFileOperationProposalAgent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class ModelBoundaryConfiguration {
    @Bean
    @ConditionalOnProperty(name = "agentic.model.provider", havingValue = "deterministic", matchIfMissing = true)
    ModelProvider deterministicModelProvider(ObjectMapper objectMapper) {
        return new DeterministicModelProvider(objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "agentic.model.provider", havingValue = "openai")
    ModelProvider openAiModelProvider(ModelProviderProperties properties, ObjectMapper objectMapper) {
        return new OpenAiResponsesModelProvider(properties, new JdkOpenAiTransport(), objectMapper);
    }

    @Bean
    BoundedModelGateway boundedModelGateway(ModelProvider provider, ModelProviderProperties properties,
                                            ObjectMapper objectMapper) {
        return new BoundedModelGateway(provider, properties, objectMapper);
    }

    @Bean
    FileOperationProposalAgent fileOperationProposalAgent(BoundedModelGateway gateway,
                                                          ModelProviderProperties properties,
                                                          ObjectMapper objectMapper) {
        return new ModelFileOperationProposalAgent(gateway, properties, objectMapper);
    }
}
