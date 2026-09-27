package com.vasanth.agenticsdlcorchestrator.model;

public final class ModelBoundaryException extends RuntimeException {
    public ModelBoundaryException(String message) {
        super(message);
    }

    public ModelBoundaryException(String message, Throwable cause) {
        super(message, cause);
    }
}
