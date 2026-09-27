package com.vasanth.agenticsdlcorchestrator;

import com.vasanth.agenticsdlcorchestrator.config.AgenticExecutionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AgenticExecutionProperties.class)
public class AgenticSdlcOrchestrator {
    public static void main(String[] args) {
        SpringApplication.run(AgenticSdlcOrchestrator.class, args);
    }
}
