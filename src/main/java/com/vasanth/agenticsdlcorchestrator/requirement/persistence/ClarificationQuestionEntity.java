package com.vasanth.agenticsdlcorchestrator.requirement.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "clarification_questions")
public class ClarificationQuestionEntity {
    @Id private UUID id;
    @Column(name = "analysis_id", nullable = false) private UUID analysisId;
    @Column(name = "question_key", nullable = false) private String questionKey;
    @Column(nullable = false) private String dimension;
    @Column(nullable = false) private String prompt;
    @Column(nullable = false) private boolean resolved;

    protected ClarificationQuestionEntity() {}

    public ClarificationQuestionEntity(UUID id, UUID analysisId, String questionKey, String dimension, String prompt) {
        this.id = id;
        this.analysisId = analysisId;
        this.questionKey = questionKey;
        this.dimension = dimension;
        this.prompt = prompt;
    }

    public void resolve() { resolved = true; }
    public UUID getId() { return id; }
    public UUID getAnalysisId() { return analysisId; }
    public String getQuestionKey() { return questionKey; }
    public String getDimension() { return dimension; }
    public String getPrompt() { return prompt; }
    public boolean isResolved() { return resolved; }
}

