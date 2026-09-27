package com.vasanth.agenticsdlcorchestrator.repository.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public final class SafePathResolver {
    private final Path root;
    private final Path realRoot;

    public SafePathResolver(Path root) {
        try {
            this.root = root.toAbsolutePath().normalize();
            this.realRoot = this.root.toRealPath(LinkOption.NOFOLLOW_LINKS);
        } catch (IOException exception) {
            throw new RepositoryAccessException("approved root must exist: " + root, exception);
        }
    }

    public Path resolveExisting(String relativePath) {
        Path relative = parseRelative(relativePath);
        Path candidate = root.resolve(relative).normalize();
        if (!candidate.startsWith(root)) throw new RepositoryAccessException("path escapes approved root");
        try {
            rejectSymbolicComponents(candidate);
            Path real = candidate.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!real.startsWith(realRoot)) throw new RepositoryAccessException("path escapes approved root");
            return real;
        } catch (IOException exception) {
            throw new RepositoryAccessException("path does not exist: " + relativePath, exception);
        }
    }

    public Path resolveForWrite(String relativePath) {
        Path relative = parseRelative(relativePath);
        if (relative.toString().equals(".")) throw new RepositoryAccessException("file path is required");
        Path candidate = root.resolve(relative).normalize();
        if (!candidate.startsWith(root)) throw new RepositoryAccessException("path escapes approved root");
        try {
            rejectSymbolicComponents(candidate);
            Path existingParent = candidate.getParent();
            while (existingParent != null && !Files.exists(existingParent, LinkOption.NOFOLLOW_LINKS)) {
                existingParent = existingParent.getParent();
            }
            if (existingParent == null || !existingParent.toRealPath(LinkOption.NOFOLLOW_LINKS).startsWith(realRoot)) {
                throw new RepositoryAccessException("path parent escapes approved root");
            }
            return candidate;
        } catch (IOException exception) {
            throw new RepositoryAccessException("path validation failed: " + relativePath, exception);
        }
    }

    public Path root() { return realRoot; }

    private Path parseRelative(String value) {
        if (value == null) throw new RepositoryAccessException("path is required");
        Path path = Path.of(value.isBlank() ? "." : value);
        if (path.isAbsolute()) throw new RepositoryAccessException("absolute paths are not allowed");
        for (Path part : path) {
            if (part.toString().equals("..")) throw new RepositoryAccessException("path traversal is not allowed");
        }
        return path;
    }

    private void rejectSymbolicComponents(Path candidate) throws IOException {
        Path current = root;
        Path relative = root.relativize(candidate);
        for (Path part : relative) {
            current = current.resolve(part);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new RepositoryAccessException("symbolic links are not allowed: " + root.relativize(current));
            }
        }
    }
}
