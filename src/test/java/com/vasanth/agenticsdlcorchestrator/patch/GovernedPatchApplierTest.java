package com.vasanth.agenticsdlcorchestrator.patch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vasanth.agenticsdlcorchestrator.config.PatchPolicyProperties;
import com.vasanth.agenticsdlcorchestrator.config.RepositoryToolProperties;
import com.vasanth.agenticsdlcorchestrator.patch.PatchModels.AgentPatchProposal;
import com.vasanth.agenticsdlcorchestrator.patch.PatchModels.FileOperation;
import com.vasanth.agenticsdlcorchestrator.patch.PatchModels.FileOperationType;
import com.vasanth.agenticsdlcorchestrator.repository.workspace.RepositoryWorkspaceService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GovernedPatchApplierTest {
    @TempDir Path temp;
    private Path source;
    private RepositoryToolProperties repositoryLimits;
    private PatchPolicyProperties patchPolicy;

    @BeforeEach
    void setUp() throws IOException {
        source = Files.createDirectories(temp.resolve("source"));
        write(source, "src/main/java/example/Existing.java", "class Existing {}\n");
        write(source, "docs/obsolete.md", "remove me\n");
        repositoryLimits = new RepositoryToolProperties(List.of(source), 100, 20_000, 1_000_000, 100);
        patchPolicy = new PatchPolicyProperties(10, 100_000, List.of("src/main", "src/test", "docs"));
    }

    @Test
    void appliesCreateUpdateDeleteExactlyAndProducesManifestAndDiff() throws IOException {
        Fixture fixture = fixture();
        String expected = sha256("class Existing {}\n");
        var proposal = proposal(List.of(
                operation("src/main/java/example/New.java", FileOperationType.CREATE, "class New {}\n", null),
                operation("src/main/java/example/Existing.java", FileOperationType.UPDATE,
                        "class Existing { int changed; }\n", expected),
                operation("docs/obsolete.md", FileOperationType.DELETE, null, sha256("remove me\n"))));

        var result = fixture.applier().apply(proposal, "a".repeat(64), fixture.baselineHash());

        assertThat(result.operations()).hasSize(3);
        assertThat(result.diff().changedPaths()).containsExactly("docs/obsolete.md",
                "src/main/java/example/Existing.java", "src/main/java/example/New.java");
        assertThat(result.diff().unifiedDiff()).contains("+class New {}", "-remove me");
        assertThat(Files.exists(fixture.repository().resolve("docs/obsolete.md"))).isFalse();
        assertThat(Files.readString(fixture.repository().resolve("src/main/java/example/Existing.java")))
                .contains("changed");
    }

    @Test
    void rejectsUnsafeDuplicateUnsupportedOversizedAndStaleOperations() {
        Fixture fixture = fixture();
        assertRejected(fixture, List.of(operation("../escape.java", FileOperationType.CREATE, "x", null)), "normalized");
        assertRejected(fixture, List.of(operation("outside/File.java", FileOperationType.CREATE, "x", null)), "permitted");
        assertRejected(fixture, List.of(operation("src/main/payload.exe", FileOperationType.CREATE, "x", null)), "unsupported");
        var duplicate = operation("src/main/java/example/New.java", FileOperationType.CREATE, "x", null);
        assertRejected(fixture, List.of(duplicate, duplicate), "duplicate");
        assertRejected(fixture, List.of(operation("src/main/java/example/Existing.java", FileOperationType.UPDATE,
                "new", "0".repeat(64))), "optimistic-lock");

        var smallPolicy = new PatchPolicyProperties(10, 1_024, List.of("src/main"));
        var limited = new GovernedPatchApplier(fixture.repository(), fixture.baseline(), fixture.workspaces(),
                repositoryLimits, smallPolicy);
        assertThatThrownBy(() -> limited.apply(proposal(List.of(operation(
                "src/main/java/example/Large.java", FileOperationType.CREATE, "x".repeat(2_000), null))),
                "b".repeat(64), fixture.baselineHash())).hasMessageContaining("byte limit");
    }

    @Test
    void restoresBaselineWhenAnAtomicOperationFailsAfterAnEarlierWrite() throws IOException {
        write(source, "src/main/blocker.java", "regular file blocks a child path");
        Fixture fixture = fixture();
        var proposal = proposal(List.of(
                operation("src/main/java/example/First.java", FileOperationType.CREATE, "class First {}", null),
                operation("src/main/blocker.java/Second.java", FileOperationType.CREATE, "class Second {}", null)));

        assertThatThrownBy(() -> fixture.applier().apply(proposal, "c".repeat(64), fixture.baselineHash()))
                .isInstanceOf(PatchPolicyException.class).hasMessageContaining("atomic file operation failed");

        assertThat(fixture.repository().resolve("src/main/java/example/First.java")).doesNotExist();
        assertThat(Files.readString(fixture.repository().resolve("src/main/java/example/Existing.java")))
                .isEqualTo("class Existing {}\n");
    }

    private void assertRejected(Fixture fixture, List<FileOperation> operations, String message) {
        assertThatThrownBy(() -> fixture.applier().apply(proposal(operations), "d".repeat(64), fixture.baselineHash()))
                .isInstanceOf(PatchPolicyException.class).hasMessageContaining(message);
    }

    private Fixture fixture() {
        Path workspaceRoot = temp.resolve("workspaces-" + UUID.randomUUID());
        RepositoryWorkspaceService workspaces = new RepositoryWorkspaceService(workspaceRoot, repositoryLimits);
        var evidence = workspaces.create(UUID.randomUUID(), 1, source);
        Path repository = Path.of(evidence.repositoryPath());
        Path baseline = Path.of(evidence.baselinePath());
        return new Fixture(repository, baseline, evidence.baselineManifest().manifestHash(), workspaces,
                new GovernedPatchApplier(repository, baseline, workspaces, repositoryLimits, patchPolicy));
    }

    private AgentPatchProposal proposal(List<FileOperation> operations) {
        return new AgentPatchProposal(UUID.randomUUID(), "IMPLEMENTATION", "deterministic", "deterministic-v1", operations);
    }

    private FileOperation operation(String path, FileOperationType type, String content, String expectedHash) {
        return new FileOperation(path, type, content, expectedHash, "Implement requirement", "REQ-1",
                List.of("AC-1"), "task-1", List.of("a".repeat(64)));
    }

    private static void write(Path root, String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private record Fixture(Path repository, Path baseline, String baselineHash,
                           RepositoryWorkspaceService workspaces, GovernedPatchApplier applier) {}
}
