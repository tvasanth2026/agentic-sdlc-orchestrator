package com.vasanth.agenticsdlcorchestrator.requirement.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClarificationQuestionRepository extends JpaRepository<ClarificationQuestionEntity, UUID> {
    List<ClarificationQuestionEntity> findByAnalysisIdOrderByQuestionKey(UUID analysisId);
}

