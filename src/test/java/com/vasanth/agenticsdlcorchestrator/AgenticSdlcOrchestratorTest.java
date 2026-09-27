package com.vasanth.agenticsdlcorchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import com.vasanth.agenticsdlcorchestrator.config.AgenticExecutionProperties;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AgenticSdlcOrchestratorTest {
    @Autowired AgenticExecutionProperties executionProperties;

    @Test
    void contextLoadsWithFlywaySchema() {
        assertThat(executionProperties.deterministic()).isTrue();
        assertThat(executionProperties.maxAttempts()).isEqualTo(3);
        assertThat(executionProperties.workspaceRoot()).isEqualTo(Path.of("target", "test-workspaces"));
        assertThat(executionProperties.clarificationToken()).isEqualTo("local-operator-token");
    }
}
