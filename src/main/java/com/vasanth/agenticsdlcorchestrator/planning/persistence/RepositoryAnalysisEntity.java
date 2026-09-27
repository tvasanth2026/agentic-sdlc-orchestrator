package com.vasanth.agenticsdlcorchestrator.planning.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "repository_analyses")
public class RepositoryAnalysisEntity {
    @Id private UUID id;
    @Column(name = "revision_id", nullable = false, unique = true) private UUID revisionId;
    @Column(name = "workspace_location", nullable = false) private String workspaceLocation;
    @Column(name = "baseline_manifest_hash", nullable = false, length = 64) private String baselineManifestHash;
    @Column(name = "analysis_json", nullable = false) private String analysisJson;
    @Column(name = "analysis_hash", nullable = false, length = 64) private String analysisHash;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected RepositoryAnalysisEntity() {}

    public RepositoryAnalysisEntity(UUID id, UUID revisionId, String workspaceLocation,
                                    String baselineManifestHash, String analysisJson,
                                    String analysisHash, Instant createdAt) {
        this.id = id;
        this.revisionId = revisionId;
        this.workspaceLocation = workspaceLocation;
        this.baselineManifestHash = baselineManifestHash;
        this.analysisJson = analysisJson;
        this.analysisHash = analysisHash;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getRevisionId() { return revisionId; }
    public String getAnalysisHash() { return analysisHash; }
    public String getWorkspaceLocation() { return workspaceLocation; }
    public String getBaselineManifestHash() { return baselineManifestHash; }
}
