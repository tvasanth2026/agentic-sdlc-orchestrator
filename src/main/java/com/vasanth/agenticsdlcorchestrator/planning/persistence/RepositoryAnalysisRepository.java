package com.vasanth.agenticsdlcorchestrator.planning.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositoryAnalysisRepository extends JpaRepository<RepositoryAnalysisEntity, UUID> {
    Optional<RepositoryAnalysisEntity> findByRevisionId(UUID revisionId);
}

