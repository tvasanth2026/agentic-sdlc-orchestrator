package com.vasanth.agenticsdlcorchestrator.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.vasanth.agenticsdlcorchestrator.config.ValidationProperties;
import com.vasanth.agenticsdlcorchestrator.validation.BuildModels.FailureClassification;
import com.vasanth.agenticsdlcorchestrator.validation.BuildModels.MavenCapability;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FixedMavenCapabilityToolTest {
    @TempDir Path temporary;
    private final ExposedTool tool = new ExposedTool();

    @Test
    void exposesOnlyFixedWrapperCapabilities() {
        List<String> verify = tool.exposedCommand(Path.of("workspace"), MavenCapability.CLEAN_VERIFY);
        List<String> test = tool.exposedCommand(Path.of("workspace"), MavenCapability.CLEAN_TEST);

        assertThat(verify).endsWith("--batch-mode", "--no-transfer-progress", "clean", "verify");
        assertThat(test).endsWith("--batch-mode", "--no-transfer-progress", "clean", "test");
        assertThat(verify).noneMatch(value -> value.contains(";") || value.contains("&&"));
    }

    @Test
    void stripsModelAndSecretCredentialsFromChildEnvironment() {
        Map<String, String> environment = new HashMap<>(Map.of(
                "PATH", "safe", "OPENAI_API_KEY", "secret", "SERVICE_TOKEN", "secret",
                "DB_PASSWORD", "secret", "JAVA_HOME", "safe"));

        tool.exposedSanitize(environment);

        assertThat(environment).containsEntry("PATH", "safe").containsEntry("JAVA_HOME", "safe");
        assertThat(environment).doesNotContainKeys("OPENAI_API_KEY", "SERVICE_TOKEN", "DB_PASSWORD");
    }

    @Test
    void capturesAndClassifiesRealCompilerFailure() throws IOException {
        Files.writeString(temporary.resolve("mvnw.cmd"),
                "@echo off\r\necho [ERROR] COMPILATION FAILURE\r\necho Tests run: 3, Failures: 0\r\nexit /b 1\r\n");
        Files.writeString(temporary.resolve("mvnw"),
                "#!/bin/sh\necho '[ERROR] COMPILATION FAILURE'\necho 'Tests run: 3, Failures: 0'\nexit 1\n");

        var evidence = tool.execute(temporary, MavenCapability.CLEAN_VERIFY);

        assertThat(evidence.exitCode()).isEqualTo(1);
        assertThat(evidence.classification()).isEqualTo(FailureClassification.COMPILER);
        assertThat(evidence.stdout()).contains("COMPILATION FAILURE");
        assertThat(evidence.discoveredTests()).isEqualTo(3);
    }

    private static final class ExposedTool extends FixedMavenCapabilityTool {
        private ExposedTool() { super(new ValidationProperties(Duration.ofSeconds(1), 4096, Duration.ZERO)); }
        List<String> exposedCommand(Path path, MavenCapability capability) { return command(path, capability); }
        void exposedSanitize(Map<String, String> environment) { sanitize(environment); }
    }
}
