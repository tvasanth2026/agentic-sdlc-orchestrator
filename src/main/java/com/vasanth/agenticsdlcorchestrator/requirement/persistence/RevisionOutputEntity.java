package com.vasanth.agenticsdlcorchestrator.requirement.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "revision_outputs")
public class RevisionOutputEntity {
    @Id private UUID id;
    @Column(name = "revision_id", nullable = false) private UUID revisionId;
    @Column(name = "output_key", nullable = false) private String outputKey;
    @Enumerated(EnumType.STRING) @Column(name = "input_dimension", nullable = false) private InputDimension inputDimension;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private OutputStatus status;
    @Column(name = "reused_from_output_id") private UUID reusedFromOutputId;

    protected RevisionOutputEntity() {}

    public RevisionOutputEntity(UUID id, UUID revisionId, String outputKey, InputDimension inputDimension,
                                OutputStatus status, UUID reusedFromOutputId) {
        this.id = id;
        this.revisionId = revisionId;
        this.outputKey = outputKey;
        this.inputDimension = inputDimension;
        this.status = status;
        this.reusedFromOutputId = reusedFromOutputId;
    }

    public void invalidate() { status = OutputStatus.INVALIDATED; }
    public void activate() { status = OutputStatus.ACTIVE; }
    public UUID getId() { return id; }
    public UUID getRevisionId() { return revisionId; }
    public String getOutputKey() { return outputKey; }
    public InputDimension getInputDimension() { return inputDimension; }
    public OutputStatus getStatus() { return status; }
    public UUID getReusedFromOutputId() { return reusedFromOutputId; }

    public enum InputDimension { REQUIREMENT, REPOSITORY }
    public enum OutputStatus { ACTIVE, PENDING, INVALIDATED, REUSED }
}
