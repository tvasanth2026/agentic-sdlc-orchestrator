package com.vasanth.agenticsdlcorchestrator.repository.tool;

import com.vasanth.agenticsdlcorchestrator.config.RepositoryToolProperties;
import com.vasanth.agenticsdlcorchestrator.repository.domain.RepositoryModels.RepositoryFile;
import com.vasanth.agenticsdlcorchestrator.repository.domain.RepositoryModels.SearchMatch;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class ControlledRepositoryTools {
    private static final Set<String> EXTENSIONS = Set.of(
            ".java", ".kt", ".kts", ".xml", ".yaml", ".yml", ".json", ".sql", ".md",
            ".properties", ".gradle", ".toml", ".txt", ".sh", ".cmd", ".ps1");
    private static final Set<String> IGNORED_DIRECTORIES = Set.of(
            ".git", ".idea", ".vscode", "target", "build", "node_modules", "agent-workspaces");

    private final SafePathResolver resolver;
    private final RepositoryToolProperties limits;

    public ControlledRepositoryTools(SafePathResolver resolver, RepositoryToolProperties limits) {
        this.resolver = resolver;
        this.limits = limits;
    }

    public List<RepositoryFile> listFiles(String relativeDirectory) {
        Path directory = resolver.resolveExisting(relativeDirectory);
        if (!Files.isDirectory(directory)) throw new RepositoryAccessException("path is not a directory");
        List<RepositoryFile> result = new ArrayList<>();
        long totalBytes = 0;
        try (var stream = Files.walk(directory)) {
            for (Path file : stream.filter(Files::isRegularFile).sorted().toList()) {
                if (isIgnored(file) || Files.isSymbolicLink(file) || !isSupported(file)) continue;
                BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
                enforceFileSize(attributes.size(), file);
                totalBytes += attributes.size();
                if (totalBytes > limits.maxTotalBytes()) throw new RepositoryAccessException("repository byte limit exceeded");
                if (result.size() >= limits.maxFiles()) throw new RepositoryAccessException("repository file-count limit exceeded");
                result.add(new RepositoryFile(relative(file), attributes.size(), sha256(Files.readAllBytes(file))));
            }
        } catch (IOException exception) {
            throw new RepositoryAccessException("repository listing failed", exception);
        }
        return List.copyOf(result);
    }

    public String readFile(String relativePath) {
        Path file = resolver.resolveExisting(relativePath);
        if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
            throw new RepositoryAccessException("path is not a regular file");
        }
        if (!isSupported(file)) throw new RepositoryAccessException("unsupported file type: " + relativePath);
        try {
            long size = Files.size(file);
            enforceFileSize(size, file);
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (MalformedInputException exception) {
            throw new RepositoryAccessException("binary or non-UTF-8 content is not allowed", exception);
        } catch (IOException exception) {
            throw new RepositoryAccessException("file read failed", exception);
        }
    }

    public List<SearchMatch> search(String relativeDirectory, String regularExpression) {
        Pattern pattern;
        try {
            pattern = Pattern.compile(regularExpression);
        } catch (RuntimeException exception) {
            throw new RepositoryAccessException("invalid search expression", exception);
        }
        List<SearchMatch> matches = new ArrayList<>();
        for (RepositoryFile file : listFiles(relativeDirectory)) {
            String content = readFile(file.relativePath());
            String[] lines = content.split("\\R", -1);
            for (int index = 0; index < lines.length; index++) {
                if (pattern.matcher(lines[index]).find()) {
                    if (matches.size() >= limits.maxSearchMatches()) {
                        throw new RepositoryAccessException("search-match limit exceeded");
                    }
                    matches.add(new SearchMatch(file.relativePath(), index + 1, truncate(lines[index], 500)));
                }
            }
        }
        return List.copyOf(matches);
    }

    public Path root() { return resolver.root(); }

    private boolean isIgnored(Path path) {
        Path relative = resolver.root().relativize(path);
        for (Path part : relative) {
            if (IGNORED_DIRECTORIES.contains(part.toString().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private boolean isSupported(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.equals("dockerfile") || name.equals("mvnw") || name.equals("gradlew")) return true;
        return EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private void enforceFileSize(long size, Path file) {
        if (size > limits.maxFileBytes()) {
            throw new RepositoryAccessException("file-size limit exceeded: " + relative(file));
        }
    }

    private String relative(Path file) {
        return resolver.root().relativize(file).toString().replace('\\', '/');
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String truncate(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }
}

