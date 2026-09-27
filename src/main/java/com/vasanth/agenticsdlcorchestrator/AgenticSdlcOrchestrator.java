package com.vasanth.agenticsdlcorchestrator;

import com.vasanth.agenticsdlcorchestrator.config.AgenticExecutionProperties;
import com.vasanth.agenticsdlcorchestrator.config.ModelProviderProperties;
import com.vasanth.agenticsdlcorchestrator.config.PatchPolicyProperties;
import com.vasanth.agenticsdlcorchestrator.config.RepositoryToolProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableConfigurationProperties({AgenticExecutionProperties.class, RepositoryToolProperties.class,
        ModelProviderProperties.class, PatchPolicyProperties.class})
@EnableAsync
public class AgenticSdlcOrchestrator {
    public static void main(String[] args) {
        SpringApplication.run(AgenticSdlcOrchestrator.class, args);
    }
}
