package com.vasanth.agenticsdlcorchestrator.requirement.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequirementItemRepository extends JpaRepository<RequirementItemEntity, UUID> {
    List<RequirementItemEntity> findByAnalysisIdOrderByItemTypeAscItemKeyAsc(UUID analysisId);
}

