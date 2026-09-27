package com.vasanth.agenticsdlcorchestrator.requirement.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClarificationRepository extends JpaRepository<ClarificationEntity, UUID> {
    List<ClarificationEntity> findByWorkflowIdOrderByCreatedAt(UUID workflowId);
}

