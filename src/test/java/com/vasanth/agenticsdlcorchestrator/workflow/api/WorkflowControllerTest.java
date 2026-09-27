package com.vasanth.agenticsdlcorchestrator.workflow.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vasanth.agenticsdlcorchestrator.requirement.application.SourceMutationGuard;
import com.vasanth.agenticsdlcorchestrator.requirement.persistence.ClarificationRepository;
import com.vasanth.agenticsdlcorchestrator.requirement.persistence.RevisionOutputEntity.InputDimension;
import com.vasanth.agenticsdlcorchestrator.requirement.persistence.RevisionOutputEntity.OutputStatus;
import com.vasanth.agenticsdlcorchestrator.requirement.persistence.RevisionOutputRepository;
import com.vasanth.agenticsdlcorchestrator.workflow.domain.WorkflowStatus;
import com.vasanth.agenticsdlcorchestrator.workflow.persistence.WorkflowRepository;
import com.vasanth.agenticsdlcorchestrator.workflow.persistence.WorkflowRevisionRepository;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowControllerTest {
    @Autowired MockMvc mvc;
    @Autowired WorkflowRepository workflows;
    @Autowired WorkflowRevisionRepository revisions;
    @Autowired RevisionOutputRepository outputs;
    @Autowired ClarificationRepository clarifications;
    @Autowired SourceMutationGuard mutationGuard;

    @Test
    void asynchronouslyNormalizesClearRequirementAndAllowsPlanning() throws Exception {
        UUID workflowId = submit("""
                Create short URLs through POST /api/v1/urls and return HTTP 201 with the generated code.
                Redirect GET /{code} with HTTP 302 and preserve the stored destination.
                """);

        awaitStatus(workflowId, WorkflowStatus.PLANNING);
        mvc.perform(get("/api/v1/workflows/{id}", workflowId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.analysis.clarificationRequired").value(false))
                .andExpect(jsonPath("$.analysis.sourceMutationAllowed").value(true))
                .andExpect(jsonPath("$.analysis.items[?(@.type == 'ACCEPTANCE_CRITERION')]").isNotEmpty());
    }

    @Test
    void clarificationCreatesRevisionAndMaintainsSelectiveLineage() throws Exception {
        UUID workflowId = submit("Add expiry to short URLs");
        awaitStatus(workflowId, WorkflowStatus.AWAITING_CLARIFICATION);
        var revisionOne = revisions.findByWorkflowIdAndRevisionNumber(workflowId, 1).orElseThrow();
        assertThatThrownBy(() -> mutationGuard.requireAllowed(revisionOne.getId()))
                .hasMessageContaining("blocked pending clarification");

        String clarification = """
                {"clarifiedRequirement":"Add URL expiry. The create API accepts expiresAt as an ISO-8601 UTC instant. Redirects after expiry return HTTP 410 and links without expiresAt never expire.",
                 "answers":{"CQ-1":"Use the create and redirect APIs described.","CQ-2":"Client UTC instant; return 410.","CQ-3":"Not applicable."}}
                """;
        mvc.perform(post("/api/v1/workflows/{id}/clarifications", workflowId)
                        .header("X-Operator-Token", "wrong-token")
                        .header("X-Operator-Id", "operator-test")
                        .contentType(MediaType.APPLICATION_JSON).content(clarification))
                .andExpect(status().isUnauthorized());

        String response = mvc.perform(post("/api/v1/workflows/{id}/clarifications", workflowId)
                        .header("X-Operator-Token", "local-operator-token")
                        .header("X-Operator-Id", "operator-test")
                        .contentType(MediaType.APPLICATION_JSON).content(clarification))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.parentRevisionId").value(revisionOne.getId().toString()))
                .andReturn().getResponse().getContentAsString();

        UUID revisionTwoId = UUID.fromString(JsonPath.read(response, "$.revisionId"));
        awaitStatus(workflowId, WorkflowStatus.PLANNING);
        mutationGuard.requireAllowed(revisionTwoId);
        assertThat(clarifications.findByWorkflowIdOrderByCreatedAt(workflowId))
                .singleElement().extracting(item -> item.getActor()).isEqualTo("operator-test");
        assertThat(outputs.findByRevisionIdOrderByOutputKey(revisionOne.getId()))
                .filteredOn(item -> item.getInputDimension() == InputDimension.REQUIREMENT)
                .allMatch(item -> item.getStatus() == OutputStatus.INVALIDATED);
        assertThat(outputs.findByRevisionIdOrderByOutputKey(revisionTwoId))
                .filteredOn(item -> item.getInputDimension() == InputDimension.REPOSITORY)
                .singleElement().satisfies(item -> {
                    assertThat(item.getStatus()).isEqualTo(OutputStatus.REUSED);
                    assertThat(item.getReusedFromOutputId()).isNotNull();
                });
        assertThat(outputs.findByRevisionIdOrderByOutputKey(revisionTwoId))
                .filteredOn(item -> item.getInputDimension() == InputDimension.REQUIREMENT)
                .allMatch(item -> item.getStatus() == OutputStatus.ACTIVE);
    }

    @Test
    void rejectsIncompleteClarificationWithoutCreatingRevision() throws Exception {
        UUID workflowId = submit("Add analytics");
        awaitStatus(workflowId, WorkflowStatus.AWAITING_CLARIFICATION);

        mvc.perform(post("/api/v1/workflows/{id}/clarifications", workflowId)
                        .header("X-Operator-Token", "local-operator-token")
                        .header("X-Operator-Id", "operator-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clarifiedRequirement":"Record redirects and return daily UTC counts from GET /analytics.",
                                 "answers":{"CQ-1":"Expose GET /analytics."}}
                                """))
                .andExpect(status().isBadRequest());
        assertThat(workflows.findById(workflowId).orElseThrow().getCurrentRevision()).isOne();
    }

    @Test
    void rejectsCallerProvidedCompletionStateAndHasNoCompletionEndpoint() throws Exception {
        mvc.perform(post("/api/v1/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requirement":"Create a short URL","repositoryPath":"repo",
                                 "state":"COMPLETED","output":"Implementation completed"}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/workflows/00000000-0000-0000-0000-000000000001/tasks/task-1/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"output\":\"Implementation completed\"}"))
                .andExpect(status().isNotFound());
    }

    private UUID submit(String requirement) throws Exception {
        String escaped = requirement.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        String response = mvc.perform(post("/api/v1/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirement\":\"" + escaped + "\",\"repositoryPath\":\"scenario-repositories/url-shortener\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.workflowId"));
    }

    private void awaitStatus(UUID workflowId, WorkflowStatus expected) {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(workflows.findById(workflowId).orElseThrow().getStatus()).isEqualTo(expected));
    }
}

