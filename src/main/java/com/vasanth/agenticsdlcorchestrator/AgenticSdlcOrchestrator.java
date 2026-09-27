package com.vasanth.agenticsdlcorchestrator;

import com.vasanth.agenticsdlcorchestrator.config.AgenticExecutionProperties;
import com.vasanth.agenticsdlcorchestrator.config.ModelProviderProperties;
import com.vasanth.agenticsdlcorchestrator.config.PatchPolicyProperties;
import com.vasanth.agenticsdlcorchestrator.config.RepositoryToolProperties;
import com.vasanth.agenticsdlcorchestrator.config.ValidationProperties;
import com.vasanth.agenticsdlcorchestrator.config.GovernanceProperties;
import com.vasanth.agenticsdlcorchestrator.coordination.CoordinationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({AgenticExecutionProperties.class, RepositoryToolProperties.class,
        ModelProviderProperties.class, PatchPolicyProperties.class, ValidationProperties.class,
        GovernanceProperties.class, CoordinationProperties.class})
@EnableAsync
@EnableScheduling
public class AgenticSdlcOrchestrator {
    public static void main(String[] args) {
        SpringApplication.run(AgenticSdlcOrchestrator.class, args);
    }
}
