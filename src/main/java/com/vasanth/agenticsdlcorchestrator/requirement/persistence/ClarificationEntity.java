package com.vasanth.agenticsdlcorchestrator.requirement.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clarifications")
public class ClarificationEntity {
    @Id private UUID id;
    @Column(name = "workflow_id", nullable = false) private UUID workflowId;
    @Column(name = "from_revision_id", nullable = false) private UUID fromRevisionId;
    @Column(name = "new_revision_id", nullable = false) private UUID newRevisionId;
    @Column(nullable = false) private String actor;
    @Column(name = "answers_json", nullable = false) private String answersJson;
    @Column(name = "clarified_requirement", nullable = false) private String clarifiedRequirement;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected ClarificationEntity() {}

    public ClarificationEntity(UUID id, UUID workflowId, UUID fromRevisionId, UUID newRevisionId,
                               String actor, String answersJson, String clarifiedRequirement, Instant createdAt) {
        this.id = id;
        this.workflowId = workflowId;
        this.fromRevisionId = fromRevisionId;
        this.newRevisionId = newRevisionId;
        this.actor = actor;
        this.answersJson = answersJson;
        this.clarifiedRequirement = clarifiedRequirement;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getWorkflowId() { return workflowId; }
    public UUID getFromRevisionId() { return fromRevisionId; }
    public UUID getNewRevisionId() { return newRevisionId; }
    public String getActor() { return actor; }
}

