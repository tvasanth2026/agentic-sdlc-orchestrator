package com.vasanth.agenticsdlcorchestrator.repository.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vasanth.agenticsdlcorchestrator.config.RepositoryToolProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryWorkspaceServiceTest {
    @TempDir Path temp;

    @Test
    void createsIsolatedSnapshotDiffAndVerifiedRollback() throws IOException {
        Path source = Files.createDirectories(temp.resolve("source"));
        Files.writeString(source.resolve("README.md"), "baseline\n");
        Files.createDirectories(source.resolve("src"));
        Files.writeString(source.resolve("src/App.java"), "class App {}\n");
        Path workspaces = temp.resolve("workspaces");
        RepositoryWorkspaceService service = service(workspaces, source);

        var workspace = service.create(UUID.randomUUID(), 1, source);
        Path repository = Path.of(workspace.repositoryPath());
        Path baseline = Path.of(workspace.baselinePath());
        Files.writeString(repository.resolve("src/App.java"), "class App { void changed() {} }\n");
        Files.writeString(repository.resolve("src/New.java"), "class New {}\n");

        var diff = service.diff(repository, baseline);
        assertThat(diff.changedPaths()).containsExactly("src/App.java", "src/New.java");
        assertThat(diff.unifiedDiff()).contains("--- a/src/App.java", "+++ b/src/New.java", "+class New {}");
        assertThat(diff.beforeManifestHash()).isEqualTo(workspace.baselineManifest().manifestHash());

        assertThatThrownBy(() -> service.rollback(repository, baseline, "0".repeat(64)))
                .hasMessageContaining("baseline manifest hash");
        var rollback = service.rollback(repository, baseline, workspace.baselineManifest().manifestHash());
        assertThat(rollback.restored()).isTrue();
        assertThat(Files.exists(repository.resolve("src/New.java"))).isFalse();
        assertThat(Files.readString(repository.resolve("src/App.java"))).isEqualTo("class App {}\n");
    }

    @Test
    void refusesDuplicateRevisionWorkspace() throws IOException {
        Path source = Files.createDirectories(temp.resolve("source"));
        Files.writeString(source.resolve("README.md"), "seed");
        RepositoryWorkspaceService service = service(temp.resolve("workspaces"), source);
        UUID workflowId = UUID.randomUUID();
        service.create(workflowId, 1, source);
        assertThatThrownBy(() -> service.create(workflowId, 1, source))
                .hasMessageContaining("already exists");
    }

    @Test
    void discardsOnlyARevisionRepository() throws IOException {
        Path source = Files.createDirectories(temp.resolve("source"));
        Files.writeString(source.resolve("README.md"), "seed");
        Path workspaces = temp.resolve("workspaces");
        RepositoryWorkspaceService service = service(workspaces, source);
        var workspace = service.create(UUID.randomUUID(), 1, source);
        Path repository = Path.of(workspace.repositoryPath());
        Path revisionRoot = repository.getParent();

        assertThatThrownBy(() -> service.discard(revisionRoot.resolve("snapshots")))
                .hasMessageContaining("not a revision repository");
        service.discard(repository);

        assertThat(revisionRoot).doesNotExist();
        assertThat(workspaces).exists();
    }

    private RepositoryWorkspaceService service(Path workspaces, Path source) {
        return new RepositoryWorkspaceService(workspaces,
                new RepositoryToolProperties(List.of(source), 100, 20_000, 1_000_000, 100));
    }
}
