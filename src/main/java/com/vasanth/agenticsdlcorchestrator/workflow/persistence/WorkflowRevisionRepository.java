package com.vasanth.agenticsdlcorchestrator.workflow.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowRevisionRepository extends JpaRepository<WorkflowRevisionEntity, UUID> {
    Optional<WorkflowRevisionEntity> findByWorkflowIdAndRevisionNumber(UUID workflowId, int revisionNumber);
}
