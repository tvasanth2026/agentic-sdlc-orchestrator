package com.vasanth.agenticsdlcorchestrator.workflow.persistence;

import com.vasanth.agenticsdlcorchestrator.workflow.domain.WorkflowStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflows")
public class WorkflowEntity {
    @Id private UUID id;
    @Column(name = "original_requirement", nullable = false) private String originalRequirement;
    @Column(name = "repository_path", nullable = false) private String repositoryPath;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private WorkflowStatus status;
    @Column(name = "current_revision", nullable = false) private int currentRevision;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected WorkflowEntity() {}

    public WorkflowEntity(UUID id, String originalRequirement, String repositoryPath, Instant now) {
        this.id = id;
        this.originalRequirement = originalRequirement;
        this.repositoryPath = repositoryPath;
        this.status = WorkflowStatus.RECEIVED;
        this.currentRevision = 1;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void transition(WorkflowStatus next, Instant now) {
        status = next;
        updatedAt = now;
    }

    public void advanceTo(int revision, Instant now) {
        if (revision != currentRevision + 1) throw new IllegalArgumentException("revision must advance by one");
        currentRevision = revision;
        status = WorkflowStatus.RECEIVED;
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getOriginalRequirement() { return originalRequirement; }
    public String getRepositoryPath() { return repositoryPath; }
    public WorkflowStatus getStatus() { return status; }
    public int getCurrentRevision() { return currentRevision; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

