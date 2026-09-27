package com.vasanth.agenticsdlcorchestrator.requirement.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "requirement_items")
public class RequirementItemEntity {
    @Id private UUID id;
    @Column(name = "analysis_id", nullable = false) private UUID analysisId;
    @Enumerated(EnumType.STRING) @Column(name = "item_type", nullable = false) private ItemType itemType;
    @Column(name = "item_key", nullable = false) private String itemKey;
    @Column(nullable = false) private String content;
    @Column(nullable = false) private boolean behavioral;

    protected RequirementItemEntity() {}

    public RequirementItemEntity(UUID id, UUID analysisId, ItemType itemType, String itemKey,
                                 String content, boolean behavioral) {
        this.id = id;
        this.analysisId = analysisId;
        this.itemType = itemType;
        this.itemKey = itemKey;
        this.content = content;
        this.behavioral = behavioral;
    }

    public UUID getId() { return id; }
    public UUID getAnalysisId() { return analysisId; }
    public ItemType getItemType() { return itemType; }
    public String getItemKey() { return itemKey; }
    public String getContent() { return content; }
    public boolean isBehavioral() { return behavioral; }

    public enum ItemType { ACCEPTANCE_CRITERION, ASSUMPTION, CONSTRAINT, RISK }
}

