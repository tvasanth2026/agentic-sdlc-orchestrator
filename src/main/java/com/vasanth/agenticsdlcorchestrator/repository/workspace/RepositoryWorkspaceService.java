package com.vasanth.agenticsdlcorchestrator.repository.workspace;

import com.vasanth.agenticsdlcorchestrator.config.RepositoryToolProperties;
import com.vasanth.agenticsdlcorchestrator.repository.domain.RepositoryModels.DiffEvidence;
import com.vasanth.agenticsdlcorchestrator.repository.domain.RepositoryModels.RepositoryFile;
import com.vasanth.agenticsdlcorchestrator.repository.domain.RepositoryModels.RollbackEvidence;
import com.vasanth.agenticsdlcorchestrator.repository.domain.RepositoryModels.SourceManifest;
import com.vasanth.agenticsdlcorchestrator.repository.domain.RepositoryModels.WorkspaceEvidence;
import com.vasanth.agenticsdlcorchestrator.repository.tool.ControlledRepositoryTools;
import com.vasanth.agenticsdlcorchestrator.repository.tool.RepositoryAccessException;
import com.vasanth.agenticsdlcorchestrator.repository.tool.SafePathResolver;
import com.vasanth.agenticsdlcorchestrator.repository.tool.SourceManifestService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RepositoryWorkspaceService {
    private final Path workspaceRoot;
    private final RepositoryToolProperties limits;

    public RepositoryWorkspaceService(Path workspaceRoot, RepositoryToolProperties limits) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.limits = limits;
        try {
            Files.createDirectories(this.workspaceRoot);
        } catch (IOException exception) {
            throw new RepositoryAccessException("cannot create workspace root", exception);
        }
    }

    public WorkspaceEvidence create(UUID workflowId, int revision, Path approvedRepositoryRoot) {
        Path revisionRoot = workspaceRoot.resolve(workflowId.toString()).resolve("revision-" + revision).normalize();
        requireWithinWorkspace(revisionRoot);
        if (Files.exists(revisionRoot)) throw new RepositoryAccessException("revision workspace already exists");
        Path repository = revisionRoot.resolve("repository");
        Path baseline = revisionRoot.resolve("snapshots").resolve("baseline");
        ControlledRepositoryTools sourceTools = tools(approvedRepositoryRoot);
        SourceManifest sourceManifest = new SourceManifestService(sourceTools).create();
        try {
            Files.createDirectories(repository);
            Files.createDirectories(baseline);
            copyManifest(sourceTools.root(), repository, sourceManifest.files());
            copyManifest(sourceTools.root(), baseline, sourceManifest.files());
        } catch (IOException exception) {
            throw new RepositoryAccessException("workspace copy failed", exception);
        }
        SourceManifest baselineManifest = manifest(baseline);
        return new WorkspaceEvidence(repository.toString(), baseline.toString(), baselineManifest);
    }

    public DiffEvidence diff(Path repository, Path baseline) {
        requireWithinWorkspace(repository.toAbsolutePath().normalize());
        requireWithinWorkspace(baseline.toAbsolutePath().normalize());
        SourceManifest before = manifest(baseline);
        SourceManifest after = manifest(repository);
        Map<String, RepositoryFile> beforeFiles = byPath(before.files());
        Map<String, RepositoryFile> afterFiles = byPath(after.files());
        Set<String> paths = new LinkedHashSet<>();
        paths.addAll(beforeFiles.keySet());
        paths.addAll(afterFiles.keySet());
        List<String> changed = paths.stream()
                .filter(path -> !same(beforeFiles.get(path), afterFiles.get(path))).sorted().toList();
        StringBuilder unified = new StringBuilder();
        ControlledRepositoryTools beforeTools = tools(baseline);
        ControlledRepositoryTools afterTools = tools(repository);
        for (String path : changed) {
            unified.append("--- a/").append(path).append('\n');
            unified.append("+++ b/").append(path).append('\n');
            String oldContent = beforeFiles.containsKey(path) ? beforeTools.readFile(path) : "";
            String newContent = afterFiles.containsKey(path) ? afterTools.readFile(path) : "";
            appendLines(unified, oldContent, '-');
            appendLines(unified, newContent, '+');
        }
        return new DiffEvidence(before.manifestHash(), after.manifestHash(), changed, unified.toString());
    }

    public RollbackEvidence rollback(Path repository, Path baseline, String expectedManifestHash) {
        Path normalizedRepository = repository.toAbsolutePath().normalize();
        Path normalizedBaseline = baseline.toAbsolutePath().normalize();
        requireWithinWorkspace(normalizedRepository);
        requireWithinWorkspace(normalizedBaseline);
        SourceManifest baselineManifest = manifest(normalizedBaseline);
        if (!baselineManifest.manifestHash().equals(expectedManifestHash)) {
            throw new RepositoryAccessException("baseline manifest hash does not match rollback request");
        }
        deleteContents(normalizedRepository);
        try {
            copyManifest(normalizedBaseline, normalizedRepository, baselineManifest.files());
        } catch (IOException exception) {
            throw new RepositoryAccessException("rollback copy failed", exception);
        }
        String actual = manifest(normalizedRepository).manifestHash();
        return new RollbackEvidence(expectedManifestHash.equals(actual), expectedManifestHash, actual);
    }

    public void discard(Path repository) {
        Path normalizedRepository = repository.toAbsolutePath().normalize();
        requireWithinWorkspace(normalizedRepository);
        if (!"repository".equals(normalizedRepository.getFileName().toString())) {
            throw new RepositoryAccessException("discard target is not a revision repository");
        }
        Path revisionRoot = normalizedRepository.getParent();
        requireWithinWorkspace(revisionRoot);
        deleteTree(revisionRoot);
    }

    private void copyManifest(Path source, Path destination, List<RepositoryFile> files) throws IOException {
        for (RepositoryFile file : files) {
            Path from = source.resolve(file.relativePath()).normalize();
            Path to = destination.resolve(file.relativePath()).normalize();
            if (!from.startsWith(source) || !to.startsWith(destination)) {
                throw new RepositoryAccessException("manifest path escapes copy root");
            }
            Files.createDirectories(to.getParent());
            Files.copy(from, to, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private void deleteContents(Path directory) {
        requireWithinWorkspace(directory);
        try (var stream = Files.walk(directory)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                if (!path.equals(directory)) Files.delete(path);
            }
        } catch (IOException exception) {
            throw new RepositoryAccessException("workspace cleanup failed", exception);
        }
    }

    private void deleteTree(Path directory) {
        requireWithinWorkspace(directory);
        try (var stream = Files.walk(directory)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        } catch (IOException exception) {
            throw new RepositoryAccessException("workspace discard failed", exception);
        }
    }

    private void appendLines(StringBuilder target, String content, char prefix) {
        if (content.isEmpty()) return;
        for (String line : content.split("\\R", -1)) target.append(prefix).append(line).append('\n');
    }

    private Map<String, RepositoryFile> byPath(List<RepositoryFile> files) {
        Map<String, RepositoryFile> result = new LinkedHashMap<>();
        files.forEach(file -> result.put(file.relativePath(), file));
        return result;
    }

    private boolean same(RepositoryFile left, RepositoryFile right) {
        return left != null && right != null && left.sha256().equals(right.sha256());
    }

    private SourceManifest manifest(Path root) {
        return new SourceManifestService(tools(root)).create();
    }

    private ControlledRepositoryTools tools(Path root) {
        return new ControlledRepositoryTools(new SafePathResolver(root), limits);
    }

    private void requireWithinWorkspace(Path path) {
        if (!path.startsWith(workspaceRoot) || path.equals(workspaceRoot)) {
            throw new RepositoryAccessException("operation is outside an isolated revision workspace");
        }
    }
}
