package com.vasanth.agenticsdlcorchestrator.config;

import com.vasanth.agenticsdlcorchestrator.validation.FixedMavenCapabilityTool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ValidationConfiguration {
    @Bean
    FixedMavenCapabilityTool fixedMavenCapabilityTool(ValidationProperties properties) {
        return new FixedMavenCapabilityTool(properties);
    }

}
