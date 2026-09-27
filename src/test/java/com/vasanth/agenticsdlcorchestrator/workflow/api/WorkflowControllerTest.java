package com.vasanth.agenticsdlcorchestrator.workflow.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WorkflowController.class)
@Import(WorkflowSubmissionService.class)
class WorkflowControllerTest {
    @Autowired MockMvc mvc;

    @Test
    void acceptsOnlyRequirementAndRepositoryLocation() throws Exception {
        mvc.perform(post("/api/v1/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requirement":"Create a short URL","repositoryPath":"scenario-repositories/greenfield"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.requirementHash").isString());
    }

    @Test
    void rejectsCallerProvidedCompletionStateAndOutput() throws Exception {
        mvc.perform(post("/api/v1/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requirement":"Create a short URL","repositoryPath":"repo",
                                 "state":"COMPLETED","output":"Implementation completed"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void hasNoManualTaskCompletionEndpoint() throws Exception {
        mvc.perform(post("/api/v1/workflows/00000000-0000-0000-0000-000000000001/tasks/task-1/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"output\":\"Implementation completed\"}"))
                .andExpect(status().isNotFound());
    }
}

