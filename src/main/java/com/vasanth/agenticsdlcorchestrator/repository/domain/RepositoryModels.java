package com.vasanth.agenticsdlcorchestrator.repository.domain;

import java.util.List;
import java.util.Map;

public final class RepositoryModels {
    private RepositoryModels() {}

    public record RepositoryFile(String relativePath, long size, String sha256) {}
    public record SearchMatch(String relativePath, int lineNumber, String line) {}
    public record SourceManifest(String root, List<RepositoryFile> files, long totalBytes, String manifestHash) {
        public SourceManifest { files = List.copyOf(files); }
    }
    public record RepositoryMap(
            List<String> modules,
            List<String> packages,
            List<String> apis,
            List<String> controllers,
            List<String> services,
            List<String> domainObjects,
            List<String> repositories,
            List<String> persistence,
            List<String> migrations,
            List<String> tests,
            List<String> dataFlows,
            List<String> buildConventions,
            Map<String, List<String>> acceptanceCriterionImpacts) {
        public RepositoryMap {
            modules = List.copyOf(modules);
            packages = List.copyOf(packages);
            apis = List.copyOf(apis);
            controllers = List.copyOf(controllers);
            services = List.copyOf(services);
            domainObjects = List.copyOf(domainObjects);
            repositories = List.copyOf(repositories);
            persistence = List.copyOf(persistence);
            migrations = List.copyOf(migrations);
            tests = List.copyOf(tests);
            dataFlows = List.copyOf(dataFlows);
            buildConventions = List.copyOf(buildConventions);
            acceptanceCriterionImpacts = Map.copyOf(acceptanceCriterionImpacts);
        }
    }
    public record WorkspaceEvidence(String repositoryPath, String baselinePath,
                                    SourceManifest baselineManifest) {}
    public record DiffEvidence(String beforeManifestHash, String afterManifestHash,
                               List<String> changedPaths, String unifiedDiff) {
        public DiffEvidence { changedPaths = List.copyOf(changedPaths); }
    }
    public record RollbackEvidence(boolean restored, String expectedManifestHash,
                                   String actualManifestHash) {}
}

