package com.vasanth.agenticsdlcorchestrator.repository.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.vasanth.agenticsdlcorchestrator.config.RepositoryToolProperties;
import com.vasanth.agenticsdlcorchestrator.repository.tool.ControlledRepositoryTools;
import com.vasanth.agenticsdlcorchestrator.repository.tool.SafePathResolver;
import com.vasanth.agenticsdlcorchestrator.requirement.domain.RequirementAnalysis.AcceptanceCriterion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryAnalyzerTest {
    @TempDir Path repository;

    @Test
    void mapsArchitectureDataFlowsTestsMigrationsAndCriterionImpacts() throws IOException {
        write("pom.xml", "<project/>");
        write("src/main/java/example/ShortUrlController.java", """
                package example;
                @RestController class ShortUrlController {
                  @PostMapping(\"/urls\") void createAlias() {}
                }
                """);
        write("src/main/java/example/ShortUrlService.java", "package example; @Service class ShortUrlService { void createAlias() {} }");
        write("src/main/java/example/ShortUrl.java", "package example; @Entity class ShortUrl {}");
        write("src/main/java/example/ShortUrlRepository.java", "package example; interface ShortUrlRepository extends JpaRepository<ShortUrl,Long> {}");
        write("src/main/resources/db/migration/V1__url.sql", "create table short_url(id bigint);");
        write("src/test/java/example/ShortUrlServiceTest.java", "class ShortUrlServiceTest {}");
        ControlledRepositoryTools tools = new ControlledRepositoryTools(new SafePathResolver(repository),
                new RepositoryToolProperties(List.of(repository), 100, 20_000, 1_000_000, 100));

        var map = new RepositoryAnalyzer(tools).analyze(List.of(
                new AcceptanceCriterion("AC-ALIAS", "Create and redirect through a custom alias", true)));

        assertThat(map.modules()).containsExactly(".");
        assertThat(map.packages()).contains("example");
        assertThat(map.controllers()).singleElement().asString().contains("ShortUrlController");
        assertThat(map.services()).singleElement().asString().contains("ShortUrlService");
        assertThat(map.repositories()).singleElement().asString().contains("ShortUrlRepository");
        assertThat(map.migrations()).singleElement().asString().contains("V1__url.sql");
        assertThat(map.tests()).singleElement().asString().contains("ShortUrlServiceTest");
        assertThat(map.dataFlows()).contains("HTTP controller -> application service", "application service -> repository");
        assertThat(map.acceptanceCriterionImpacts().get("AC-ALIAS")).isNotEmpty();
    }

    private void write(String relative, String content) throws IOException {
        Path file = repository.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
