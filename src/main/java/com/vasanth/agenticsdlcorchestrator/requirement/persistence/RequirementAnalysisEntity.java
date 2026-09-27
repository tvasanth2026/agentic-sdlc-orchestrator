package com.vasanth.agenticsdlcorchestrator.requirement.persistence;

import com.vasanth.agenticsdlcorchestrator.requirement.domain.RequirementAnalysis.RiskLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "requirement_analyses")
public class RequirementAnalysisEntity {
    @Id private UUID id;
    @Column(name = "revision_id", nullable = false, unique = true) private UUID revisionId;
    @Column(name = "normalized_problem", nullable = false) private String normalizedProblem;
    @Column(name = "ambiguity_required", nullable = false) private boolean ambiguityRequired;
    @Enumerated(EnumType.STRING) @Column(name = "risk_level", nullable = false) private RiskLevel riskLevel;
    @Column(name = "source_mutation_allowed", nullable = false) private boolean sourceMutationAllowed;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected RequirementAnalysisEntity() {}

    public RequirementAnalysisEntity(UUID id, UUID revisionId, String normalizedProblem,
                                     boolean ambiguityRequired, RiskLevel riskLevel,
                                     boolean sourceMutationAllowed, Instant createdAt) {
        this.id = id;
        this.revisionId = revisionId;
        this.normalizedProblem = normalizedProblem;
        this.ambiguityRequired = ambiguityRequired;
        this.riskLevel = riskLevel;
        this.sourceMutationAllowed = sourceMutationAllowed;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getRevisionId() { return revisionId; }
    public String getNormalizedProblem() { return normalizedProblem; }
    public boolean isAmbiguityRequired() { return ambiguityRequired; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public boolean isSourceMutationAllowed() { return sourceMutationAllowed; }
}

