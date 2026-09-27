package com.vasanth.agenticsdlcorchestrator.planning.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "engineering_plans")
public class EngineeringPlanEntity {
    @Id private UUID id;
    @Column(name = "revision_id", nullable = false, unique = true) private UUID revisionId;
    @Column(name = "requirement_hash", nullable = false, length = 64) private String requirementHash;
    @Column(name = "repository_analysis_hash", nullable = false, length = 64) private String repositoryAnalysisHash;
    @Column(name = "plan_json", nullable = false) private String planJson;
    @Column(name = "plan_hash", nullable = false, length = 64) private String planHash;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected EngineeringPlanEntity() {}

    public EngineeringPlanEntity(UUID id, UUID revisionId, String requirementHash,
                                 String repositoryAnalysisHash, String planJson,
                                 String planHash, Instant createdAt) {
        this.id = id;
        this.revisionId = revisionId;
        this.requirementHash = requirementHash;
        this.repositoryAnalysisHash = repositoryAnalysisHash;
        this.planJson = planJson;
        this.planHash = planHash;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getRevisionId() { return revisionId; }
    public String getPlanHash() { return planHash; }
    public String getRequirementHash() { return requirementHash; }
    public String getRepositoryAnalysisHash() { return repositoryAnalysisHash; }
}
