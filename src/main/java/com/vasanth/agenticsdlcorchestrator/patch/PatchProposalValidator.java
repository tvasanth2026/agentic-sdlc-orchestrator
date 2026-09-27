package com.vasanth.agenticsdlcorchestrator.patch;

import com.vasanth.agenticsdlcorchestrator.config.PatchPolicyProperties;
import com.vasanth.agenticsdlcorchestrator.patch.PatchModels.AgentPatchProposal;
import com.vasanth.agenticsdlcorchestrator.patch.PatchModels.FileOperation;
import com.vasanth.agenticsdlcorchestrator.patch.PatchModels.FileOperationType;
import com.vasanth.agenticsdlcorchestrator.repository.tool.SafePathResolver;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class PatchProposalValidator {
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> EXTENSIONS = Set.of(
            ".java", ".kt", ".kts", ".xml", ".yaml", ".yml", ".json", ".sql", ".md",
            ".properties", ".gradle", ".toml", ".txt", ".sh", ".cmd", ".ps1");

    private final SafePathResolver resolver;
    private final PatchPolicyProperties policy;

    public PatchProposalValidator(SafePathResolver resolver, PatchPolicyProperties policy) {
        this.resolver = resolver;
        this.policy = policy;
    }

    public AgentPatchProposal validate(AgentPatchProposal proposal) {
        if (proposal == null || proposal.id() == null || blank(proposal.agentRole())
                || blank(proposal.provider()) || blank(proposal.model())) {
            throw new PatchPolicyException("proposal identity, agent role, provider and model are required");
        }
        if (!Set.of("IMPLEMENTATION", "TEST_GENERATION", "REPAIR").contains(proposal.agentRole())) {
            throw new PatchPolicyException("agent role cannot propose file operations");
        }
        if (proposal.operations().isEmpty() || proposal.operations().size() > policy.maxOperations()) {
            throw new PatchPolicyException("proposal operation count is outside configured limits");
        }
        Set<String> paths = new HashSet<>();
        long bytes = 0;
        for (FileOperation operation : proposal.operations()) {
            validateOperation(operation, paths);
            if (operation.content() != null) bytes += operation.content().getBytes(StandardCharsets.UTF_8).length;
            if (bytes > policy.maxPatchBytes()) throw new PatchPolicyException("proposal byte limit exceeded");
        }
        return proposal;
    }

    private void validateOperation(FileOperation operation, Set<String> paths) {
        if (operation == null || operation.operationType() == null || blank(operation.relativePath())) {
            throw new PatchPolicyException("operation type and relative path are required");
        }
        String path = operation.relativePath();
        String normalized = Path.of(path).normalize().toString().replace('\\', '/');
        boolean traversal = false;
        for (Path part : Path.of(path)) if (part.toString().equals("..")) traversal = true;
        if (!path.equals(normalized) || traversal || path.startsWith("/") || path.contains(":")) {
            throw new PatchPolicyException("operation path must be normalized and relative: " + path);
        }
        if (!paths.add(path)) throw new PatchPolicyException("duplicate operation path: " + path);
        Path target = resolver.resolveForWrite(path);
        if (!permitted(path)) throw new PatchPolicyException("operation path is outside permitted patch roots: " + path);
        if (!supported(target)) throw new PatchPolicyException("unsupported patch file type: " + path);
        if (blank(operation.reason()) || blank(operation.requirementId()) || blank(operation.taskId())
                || operation.acceptanceCriterionIds().isEmpty() || operation.inputArtifactHashes().isEmpty()) {
            throw new PatchPolicyException("operation traceability fields are required: " + path);
        }
        operation.inputArtifactHashes().forEach(hash -> requireHash(hash, "input artifact hash"));
        boolean exists = Files.exists(target);
        if (operation.operationType() == FileOperationType.CREATE) {
            if (exists) throw new PatchPolicyException("CREATE target already exists: " + path);
            if (operation.content() == null || operation.expectedSha256() != null) {
                throw new PatchPolicyException("CREATE requires content and no expected hash: " + path);
            }
        } else {
            if (!exists || !Files.isRegularFile(target)) {
                throw new PatchPolicyException(operation.operationType() + " target does not exist: " + path);
            }
            requireHash(operation.expectedSha256(), "expected hash");
            if (operation.operationType() == FileOperationType.UPDATE && operation.content() == null) {
                throw new PatchPolicyException("UPDATE requires complete content: " + path);
            }
            if (operation.operationType() == FileOperationType.DELETE && operation.content() != null) {
                throw new PatchPolicyException("DELETE cannot include content: " + path);
            }
        }
    }

    private boolean permitted(String path) {
        return policy.permittedRoots().stream().map(root -> root.replace('\\', '/').replaceAll("/+$", ""))
                .anyMatch(root -> path.equals(root) || path.startsWith(root + "/"));
    }

    private boolean supported(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.equals("dockerfile") || name.equals("mvnw") || name.equals("gradlew")
                || EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private void requireHash(String value, String name) {
        if (value == null || !SHA256.matcher(value).matches()) throw new PatchPolicyException(name + " is invalid");
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
