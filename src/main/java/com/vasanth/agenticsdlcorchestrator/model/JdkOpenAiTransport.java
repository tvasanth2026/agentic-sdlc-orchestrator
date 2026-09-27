package com.vasanth.agenticsdlcorchestrator.model;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class JdkOpenAiTransport implements OpenAiTransport {
    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @Override
    public String post(String endpoint, String apiKey, String requestJson, Duration timeout) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ModelBoundaryException("OpenAI Responses API returned HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException exception) {
            throw new ModelBoundaryException("OpenAI Responses API request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ModelBoundaryException("OpenAI Responses API request interrupted", exception);
        }
    }
}
