package com.vasanth.agenticsdlcorchestrator.repository.tool;

import com.vasanth.agenticsdlcorchestrator.repository.domain.RepositoryModels.RepositoryFile;
import com.vasanth.agenticsdlcorchestrator.repository.domain.RepositoryModels.SourceManifest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public final class SourceManifestService {
    private final ControlledRepositoryTools tools;

    public SourceManifestService(ControlledRepositoryTools tools) {
        this.tools = tools;
    }

    public SourceManifest create() {
        List<RepositoryFile> files = tools.listFiles(".");
        long totalBytes = files.stream().mapToLong(RepositoryFile::size).sum();
        String canonical = files.stream()
                .map(file -> file.relativePath() + ":" + file.size() + ":" + file.sha256())
                .reduce("", (left, right) -> left + right + "\n");
        return new SourceManifest(tools.root().toString(), files, totalBytes, sha256(canonical));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}

