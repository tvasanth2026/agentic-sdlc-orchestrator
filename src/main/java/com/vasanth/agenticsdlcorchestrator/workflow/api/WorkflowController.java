package com.vasanth.agenticsdlcorchestrator.workflow.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workflows")
class WorkflowController {
    private final WorkflowSubmissionService service;

    WorkflowController(WorkflowSubmissionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    WorkflowSubmissionResponse submit(@Valid @RequestBody CreateWorkflowRequest request) {
        return service.submit(request);
    }
}

