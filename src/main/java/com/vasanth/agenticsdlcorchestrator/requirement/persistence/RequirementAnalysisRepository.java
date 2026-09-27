package com.vasanth.agenticsdlcorchestrator.requirement.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequirementAnalysisRepository extends JpaRepository<RequirementAnalysisEntity, UUID> {
    Optional<RequirementAnalysisEntity> findByRevisionId(UUID revisionId);
}

