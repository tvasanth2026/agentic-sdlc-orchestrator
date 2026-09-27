package com.vasanth.agenticsdlcorchestrator.requirement.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RevisionOutputRepository extends JpaRepository<RevisionOutputEntity, UUID> {
    List<RevisionOutputEntity> findByRevisionIdOrderByOutputKey(UUID revisionId);
}
