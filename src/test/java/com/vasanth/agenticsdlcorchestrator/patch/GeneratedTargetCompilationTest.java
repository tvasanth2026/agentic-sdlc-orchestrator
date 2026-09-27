package com.vasanth.agenticsdlcorchestrator.patch;

import static org.assertj.core.api.Assertions.assertThat;

import com.vasanth.agenticsdlcorchestrator.config.ModelProviderProperties;
import com.vasanth.agenticsdlcorchestrator.model.BoundedModelGateway;
import com.vasanth.agenticsdlcorchestrator.model.DeterministicModelProvider;
import com.vasanth.agenticsdlcorchestrator.patch.FileOperationProposalAgent.ProposalContext;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class GeneratedTargetCompilationTest {
    @TempDir Path workspace;

    @Test
    void generatedProductionAndTestsCompileAsOneConnectedApplication() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var properties = new ModelProviderProperties("deterministic", "deterministic-v1",
                URI.create("https://example.invalid"), "", Duration.ofSeconds(2), 60_000, 60_000);
        var gateway = new BoundedModelGateway(new DeterministicModelProvider(mapper), properties, mapper);
        var agent = new ModelFileOperationProposalAgent(gateway, properties, mapper);
        var proposals = agent.propose(new ProposalContext("REV-1", "complete hardened URL shortener",
                List.of("AC-1", "AC-2"), "a".repeat(64), "b".repeat(64), "c".repeat(64), workspace.toString()));

        var sources = proposals.stream().flatMap(proposal -> proposal.operations().stream()).map(operation -> {
            try {
                Path file = workspace.resolve(operation.relativePath());
                Files.createDirectories(file.getParent());
                Files.writeString(file, operation.content());
                return file.toFile();
            } catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
        }).toList();
        Path classes = Files.createDirectories(workspace.resolve("classes"));
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new java.io.ByteArrayOutputStream();
        var arguments = new java.util.ArrayList<>(List.of("-classpath", System.getProperty("java.class.path"),
                "-d", classes.toString()));
        arguments.addAll(sources.stream().map(java.io.File::getAbsolutePath).toList());
        int result = compiler.run(null, null, diagnostics, arguments.toArray(String[]::new));

        assertThat(result).withFailMessage(diagnostics.toString(java.nio.charset.StandardCharsets.UTF_8)).isZero();
        assertThat(sources).hasSize(5);
    }
}
