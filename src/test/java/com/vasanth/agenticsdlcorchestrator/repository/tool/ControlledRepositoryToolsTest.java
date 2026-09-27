package com.vasanth.agenticsdlcorchestrator.repository.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vasanth.agenticsdlcorchestrator.config.RepositoryToolProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ControlledRepositoryToolsTest {
    @TempDir Path temp;

    @Test
    void safelyListsReadsSearchesAndHashesSupportedFiles() throws IOException {
        Files.createDirectories(temp.resolve("src/main/java/example"));
        Files.writeString(temp.resolve("src/main/java/example/Api.java"),
                "package example;\n@RestController class Api { String redirect() { return \"ok\"; } }\n");
        Files.createDirectories(temp.resolve("target"));
        Files.writeString(temp.resolve("target/generated.java"), "class Ignored {}");
        Files.writeString(temp.resolve("secret.exe"), "unsupported");
        ControlledRepositoryTools tools = tools(100, 10_000, 100_000, 20);

        assertThat(tools.listFiles(".")).extracting("relativePath")
                .containsExactly("src/main/java/example/Api.java");
        assertThat(tools.readFile("src/main/java/example/Api.java")).contains("redirect");
        assertThat(tools.search(".", "redirect\\(")).singleElement().satisfies(match -> {
            assertThat(match.relativePath()).isEqualTo("src/main/java/example/Api.java");
            assertThat(match.lineNumber()).isEqualTo(2);
        });
        assertThat(new SourceManifestService(tools).create().manifestHash()).hasSize(64);
    }

    @Test
    void rejectsAbsoluteTraversalUnsupportedAndOversizedReads() throws IOException {
        Files.writeString(temp.resolve("large.java"), "x".repeat(100));
        Files.writeString(temp.resolve("binary.exe"), "x");
        ControlledRepositoryTools tools = tools(10, 20, 1_000, 10);

        assertThatThrownBy(() -> tools.readFile(temp.resolve("large.java").toString()))
                .hasMessageContaining("absolute paths");
        assertThatThrownBy(() -> tools.readFile("../outside.java"))
                .hasMessageContaining("traversal");
        assertThatThrownBy(() -> tools.readFile("binary.exe"))
                .hasMessageContaining("unsupported");
        assertThatThrownBy(() -> tools.readFile("large.java"))
                .hasMessageContaining("file-size limit");
    }

    @Test
    void rejectsExcessiveFileCountsAndSearchMatches() throws IOException {
        Files.writeString(temp.resolve("one.java"), "match\nmatch\n");
        Files.writeString(temp.resolve("two.java"), "match\n");
        assertThatThrownBy(() -> tools(1, 100, 1_000, 10).listFiles("."))
                .hasMessageContaining("file-count limit");
        assertThatThrownBy(() -> tools(10, 100, 1_000, 2).search(".", "match"))
                .hasMessageContaining("search-match limit");
    }

    @Test
    void rejectsSymbolicLinkEscapeWhenPlatformSupportsLinks() throws IOException {
        Path outside = Files.createTempDirectory("outside-repository");
        Path outsideFile = Files.writeString(outside.resolve("outside.java"), "class Outside {}");
        Path link = temp.resolve("linked.java");
        try {
            Files.createSymbolicLink(link, outsideFile);
        } catch (UnsupportedOperationException | IOException exception) {
            assertThat(new SafePathResolver(temp).root()).isEqualTo(temp.toRealPath());
            return;
        }
        assertThatThrownBy(() -> tools(10, 1_000, 10_000, 10).readFile("linked.java"))
                .hasMessageContaining("symbolic links");
    }

    private ControlledRepositoryTools tools(int maxFiles, long maxFileBytes, long maxTotalBytes, int maxMatches) {
        RepositoryToolProperties limits = new RepositoryToolProperties(
                List.of(temp), maxFiles, maxFileBytes, maxTotalBytes, maxMatches);
        return new ControlledRepositoryTools(new SafePathResolver(temp), limits);
    }
}

