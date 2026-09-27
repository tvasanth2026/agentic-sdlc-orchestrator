package com.vasanth.agenticsdlcorchestrator.workflow.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowRepository extends JpaRepository<WorkflowEntity, UUID> {
}

