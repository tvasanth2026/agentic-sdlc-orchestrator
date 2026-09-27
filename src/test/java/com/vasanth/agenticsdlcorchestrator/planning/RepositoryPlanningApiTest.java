package com.vasanth.agenticsdlcorchestrator.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vasanth.agenticsdlcorchestrator.planning.persistence.EngineeringPlanRepository;
import com.vasanth.agenticsdlcorchestrator.planning.persistence.RepositoryAnalysisRepository;
import com.vasanth.agenticsdlcorchestrator.workflow.domain.WorkflowStatus;
import com.vasanth.agenticsdlcorchestrator.workflow.persistence.WorkflowRepository;
import com.vasanth.agenticsdlcorchestrator.workflow.persistence.WorkflowRevisionRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RepositoryPlanningApiTest {
    private static final Path REPOSITORIES = Path.of("target/test-repositories");
    private static final Path WORKSPACES = Path.of("target/test-workspaces");

    @Autowired MockMvc mvc;
    @Autowired WorkflowRepository workflows;
    @Autowired WorkflowRevisionRepository revisions;
    @Autowired RepositoryAnalysisRepository analyses;
    @Autowired EngineeringPlanRepository plans;
    @Autowired JdbcTemplate jdbc;

    @BeforeAll
    static void createRepositoryFixture() throws IOException {
        Path repository = REPOSITORIES.resolve("url-shortener");
        write(repository, "pom.xml", "<project/>");
        write(repository, "src/main/java/example/UrlController.java",
                "package example; @RestController class UrlController { @PostMapping(\"/urls\") void create() {} }");
        write(repository, "src/main/java/example/UrlService.java",
                "package example; @Service class UrlService { String create() { return \"code\"; } }");
        write(repository, "src/test/java/example/UrlServiceTest.java", "class UrlServiceTest {}");
    }

    @AfterAll
    static void cleanFixtures() throws IOException {
        deleteTree(REPOSITORIES);
        deleteTree(WORKSPACES);
    }

    @Test
    void createsWorkspacePersistsAnalysisAndBuildsValidatedPlan() throws Exception {
        UUID workflowId = submit("Create short URLs with POST /urls returning HTTP 201 and redirect GET /{code} with HTTP 302.",
                "url-shortener");
        awaitStatus(workflowId, WorkflowStatus.PLANNING);

        String response = mvc.perform(post("/api/v1/workflows/{id}/plan", workflowId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("AWAITING_CHANGE_APPROVAL"))
                .andExpect(jsonPath("$.baselineManifestHash").isString())
                .andExpect(jsonPath("$.repositoryAnalysis.controllers[0]").value("src/main/java/example/UrlController.java"))
                .andExpect(jsonPath("$.plan.tasks[?(@.agentRole == 'VALIDATION')]").isNotEmpty())
                .andExpect(jsonPath("$.plan.tasks[?(@.agentRole == 'RISK')]").isNotEmpty())
                .andExpect(jsonPath("$.plan.tasks[?(@.agentRole == 'RELEASE_READINESS')]").isNotEmpty())
                .andExpect(jsonPath("$.agentInvocations.length()").value(12))
                .andExpect(jsonPath("$.agentInvocations[?(@.role == 'ARCHITECTURE')]").isNotEmpty())
                .andExpect(jsonPath("$.agentInvocations[?(@.role == 'RELEASE_READINESS' && @.output.ready == false)]").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        UUID revisionId = UUID.fromString(JsonPath.read(response, "$.revisionId"));
        String planHash = JsonPath.read(response, "$.planHash");
        assertThat(analyses.findByRevisionId(revisionId)).isPresent();
        assertThat(plans.findByRevisionId(revisionId)).isPresent();
        assertThat(workflows.findById(workflowId).orElseThrow().getStatus())
                .isEqualTo(WorkflowStatus.AWAITING_CHANGE_APPROVAL);
        assertThat(jdbc.queryForObject("select count(*) from agent_invocations where revision_id = ?",
                Integer.class, revisionId)).isEqualTo(12);
        assertThat(jdbc.queryForObject("select count(*) from engineering_artifacts where revision_id = ? "
                + "and validation_status = 'PASSED'", Integer.class, revisionId)).isEqualTo(12);
        assertThat(jdbc.queryForObject("select count(*) from agent_invocations where revision_id = ? "
                + "and length(output_json) > 0", Integer.class, revisionId)).isEqualTo(12);
        assertThat(jdbc.queryForObject("select count(*) from task_dependencies d join agent_tasks t "
                + "on t.id = d.task_id where t.revision_id = ?", Integer.class, revisionId)).isEqualTo(11);
        mvc.perform(post("/api/v1/workflows/{id}/changes/apply", workflowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planHash\":\"" + "0".repeat(64) + "\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/workflows/{id}/changes/apply", workflowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planHash\":\"" + planHash + "\",\"operations\":[]}"))
                .andExpect(status().isBadRequest());
        String applied = mvc.perform(post("/api/v1/workflows/{id}/changes/apply", workflowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planHash\":\"" + planHash + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("EXECUTING"))
                .andExpect(jsonPath("$.proposalIds.length()").value(2))
                .andExpect(jsonPath("$.changedPaths.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(applied, "$.changedPaths"))
                .allMatch(path -> path.startsWith("src/main/") || path.startsWith("src/test/"));
        assertThat(jdbc.queryForObject("select count(*) from patch_proposals where revision_id = ?",
                Integer.class, revisionId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from applied_file_operations a join patch_proposals p "
                + "on p.id=a.proposal_id where p.revision_id=?", Integer.class, revisionId)).isEqualTo(2);
        assertThat(workflows.findById(workflowId).orElseThrow().getStatus()).isEqualTo(WorkflowStatus.EXECUTING);
        mvc.perform(post("/api/v1/workflows/{id}/plan", workflowId)).andExpect(status().isConflict());
    }

    @Test
    void rejectsRepositoryOutsideApprovedRootBeforeWorkspaceCreation() throws Exception {
        UUID workflowId = submit("Create short URLs with POST /urls returning HTTP 201 and redirect GET /{code} with HTTP 302.",
                "../outside");
        awaitStatus(workflowId, WorkflowStatus.PLANNING);
        mvc.perform(post("/api/v1/workflows/{id}/plan", workflowId)).andExpect(status().isBadRequest());
        assertThat(revisions.findByWorkflowIdAndRevisionNumber(workflowId, 1)).isPresent();
    }

    private UUID submit(String requirement, String repositoryPath) throws Exception {
        String response = mvc.perform(post("/api/v1/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirement\":\"" + requirement.replace("\"", "\\\"")
                                + "\",\"repositoryPath\":\"" + repositoryPath + "\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.workflowId"));
    }

    private void awaitStatus(UUID workflowId, WorkflowStatus status) {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(workflows.findById(workflowId).orElseThrow().getStatus()).isEqualTo(status));
    }

    private static void write(Path root, String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
