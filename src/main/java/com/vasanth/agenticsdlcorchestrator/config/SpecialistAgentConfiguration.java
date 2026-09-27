package com.vasanth.agenticsdlcorchestrator.config;

import com.vasanth.agenticsdlcorchestrator.agent.ModelBackedSpecialistAgent;
import com.vasanth.agenticsdlcorchestrator.agent.SpecialistAgent;
import com.vasanth.agenticsdlcorchestrator.agent.SpecialistAgentRole;
import com.vasanth.agenticsdlcorchestrator.model.BoundedModelGateway;
import java.util.Arrays;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SpecialistAgentConfiguration {
    @Bean
    List<SpecialistAgent> specialistAgents(BoundedModelGateway gateway, ObjectMapper objectMapper,
                                            ModelProviderProperties properties) {
        return Arrays.stream(SpecialistAgentRole.values())
                .map(role -> (SpecialistAgent) new ModelBackedSpecialistAgent(
                        role, gateway, objectMapper, properties.maxOutputCharacters()))
                .toList();
    }
}
