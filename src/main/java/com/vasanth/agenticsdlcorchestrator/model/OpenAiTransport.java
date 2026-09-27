package com.vasanth.agenticsdlcorchestrator.model;

import java.time.Duration;

public interface OpenAiTransport {
    String post(String endpoint, String apiKey, String requestJson, Duration timeout);
}
