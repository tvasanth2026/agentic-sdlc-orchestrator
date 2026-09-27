package com.vasanth.agenticsdlcorchestrator.repository.tool;

public class RepositoryAccessException extends RuntimeException {
    public RepositoryAccessException(String message) { super(message); }
    public RepositoryAccessException(String message, Throwable cause) { super(message, cause); }
}

