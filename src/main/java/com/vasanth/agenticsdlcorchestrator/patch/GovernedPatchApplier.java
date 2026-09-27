package com.vasanth.agenticsdlcorchestrator.patch;

import com.vasanth.agenticsdlcorchestrator.config.PatchPolicyProperties;
import com.vasanth.agenticsdlcorchestrator.config.RepositoryToolProperties;
import com.vasanth.agenticsdlcorchestrator.patch.PatchModels.AgentPatchProposal;
import com.vasanth.agenticsdlcorchestrator.patch.PatchModels.AppliedFileOperation;
import com.vasanth.agenticsdlcorchestrator.patch.PatchModels.FileOperation;
import com.vasanth.agenticsdlcorchestrator.patch.PatchModels.FileOperationType;
import com.vasanth.agenticsdlcorchestrator.patch.PatchModels.PatchApplicationResult;
import com.vasanth.agenticsdlcorchestrator.repository.tool.ControlledRepositoryTools;
import com.vasanth.agenticsdlcorchestrator.repository.tool.SafePathResolver;
import com.vasanth.agenticsdlcorchestrator.repository.tool.SourceManifestService;
import com.vasanth.agenticsdlcorchestrator.repository.workspace.RepositoryWorkspaceService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public final class GovernedPatchApplier {
    private final Path repository;
    private final Path baseline;
    private final SafePathResolver resolver;
    private final RepositoryWorkspaceService workspaces;
    private final RepositoryToolProperties repositoryLimits;
    private final PatchPolicyProperties patchPolicy;

    public GovernedPatchApplier(Path repository, Path baseline, RepositoryWorkspaceService workspaces,
                                RepositoryToolProperties repositoryLimits, PatchPolicyProperties patchPolicy) {
        this.repository = repository.toAbsolutePath().normalize();
        this.baseline = baseline.toAbsolutePath().normalize();
        this.resolver = new SafePathResolver(this.repository);
        this.workspaces = workspaces;
        this.repositoryLimits = repositoryLimits;
        this.patchPolicy = patchPolicy;
    }

    public PatchApplicationResult apply(AgentPatchProposal proposal, String proposalHash,
                                        String baselineManifestHash) {
        new PatchProposalValidator(resolver, patchPolicy).validate(proposal);
        List<PreparedOperation> prepared = proposal.operations().stream().map(this::prepare).toList();
        List<AppliedFileOperation> applied = new ArrayList<>();
        try {
            for (PreparedOperation operation : prepared) applied.add(apply(operation));
            var manifest = new SourceManifestService(new ControlledRepositoryTools(
                    new SafePathResolver(repository), repositoryLimits)).create();
            return new PatchApplicationResult(proposalHash, manifest, workspaces.diff(repository, baseline), applied);
        } catch (RuntimeException exception) {
            rollback(baselineManifestHash, exception);
            throw exception;
        }
    }

    private PreparedOperation prepare(FileOperation operation) {
        Path target = resolver.resolveForWrite(operation.relativePath());
        String beforeHash = Files.exists(target) ? hash(read(target)) : null;
        if (operation.operationType() != FileOperationType.CREATE
                && !operation.expectedSha256().equals(beforeHash)) {
            throw new PatchPolicyException("optimistic-lock hash mismatch: " + operation.relativePath());
        }
        return new PreparedOperation(operation, target, beforeHash);
    }

    private AppliedFileOperation apply(PreparedOperation prepared) {
        FileOperation operation = prepared.operation();
        try {
            if (operation.operationType() == FileOperationType.DELETE) {
                Files.delete(prepared.target());
                return new AppliedFileOperation(operation.relativePath(), operation.operationType(),
                        prepared.beforeHash(), null);
            }
            Files.createDirectories(prepared.target().getParent());
            byte[] content = operation.content().getBytes(StandardCharsets.UTF_8);
            Path temporary = Files.createTempFile(prepared.target().getParent(), ".agentic-", ".tmp");
            try {
                Files.write(temporary, content);
                try {
                    Files.move(temporary, prepared.target(), StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, prepared.target(), StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            return new AppliedFileOperation(operation.relativePath(), operation.operationType(),
                    prepared.beforeHash(), hash(content));
        } catch (IOException exception) {
            throw new PatchPolicyException("atomic file operation failed: " + operation.relativePath(), exception);
        }
    }

    private void rollback(String baselineManifestHash, RuntimeException original) {
        try {
            var evidence = workspaces.rollback(repository, baseline, baselineManifestHash);
            if (!evidence.restored()) original.addSuppressed(new IllegalStateException("rollback verification failed"));
        } catch (RuntimeException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private byte[] read(Path path) {
        try { return Files.readAllBytes(path); }
        catch (IOException exception) { throw new PatchPolicyException("file read failed: " + path, exception); }
    }

    private String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }

    private record PreparedOperation(FileOperation operation, Path target, String beforeHash) {}
}
