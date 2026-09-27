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
@Table(name = "workflow_revisions")
public class WorkflowRevisionEntity {
    @Id private UUID id;
    @Column(name = "workflow_id", nullable = false) private UUID workflowId;
    @Column(name = "revision_number", nullable = false) private int revisionNumber;
    @Column(name = "parent_revision_id") private UUID parentRevisionId;
    @Column(name = "requirement_hash", nullable = false, length = 64) private String requirementHash;
    @Enumerated(EnumType.STRING) @Column(name = "state", nullable = false) private WorkflowStatus state;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected WorkflowRevisionEntity() {}

    public WorkflowRevisionEntity(UUID id, UUID workflowId, int revisionNumber, UUID parentRevisionId,
                                  String requirementHash, Instant createdAt) {
        this.id = id;
        this.workflowId = workflowId;
        this.revisionNumber = revisionNumber;
        this.parentRevisionId = parentRevisionId;
        this.requirementHash = requirementHash;
        this.state = WorkflowStatus.RECEIVED;
        this.createdAt = createdAt;
    }

    public void transition(WorkflowStatus next) { state = next; }
    public UUID getId() { return id; }
    public UUID getWorkflowId() { return workflowId; }
    public int getRevisionNumber() { return revisionNumber; }
    public UUID getParentRevisionId() { return parentRevisionId; }
    public String getRequirementHash() { return requirementHash; }
    public WorkflowStatus getState() { return state; }
}

