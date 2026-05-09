package com.desktoppet.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

final class DashScopeVlEmbeddingModel implements EmbeddingModel {
    private static final String MULTIMODAL_EMBEDDING_PATH =
            "/api/v1/services/embeddings/multimodal-embedding/multimodal-embedding";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final URI endpoint;
    private final String apiKey;
    private final String modelName;
    private final int dimensions;

    DashScopeVlEmbeddingModel(String baseUrl, String apiKey, String modelName, int dimensions, Duration timeout) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        this.endpoint = nativeEndpoint(baseUrl);
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.dimensions = dimensions;
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        if (textSegments == null || textSegments.isEmpty()) {
            return Response.from(List.of());
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(45))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody(textSegments)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(errorMessage(root, response.statusCode()));
            }
            return Response.from(embeddings(root));
        } catch (IOException e) {
            throw new IllegalStateException("DashScope embedding request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("DashScope embedding request interrupted", e);
        }
    }

    @Override
    public int dimension() {
        return dimensions;
    }

    @Override
    public String modelName() {
        return modelName;
    }

    private String requestBody(List<TextSegment> textSegments) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", modelName);

        ObjectNode input = root.putObject("input");
        ArrayNode contents = input.putArray("contents");
        for (TextSegment segment : textSegments) {
            contents.addObject().put("text", segment.text());
        }

        ObjectNode parameters = root.putObject("parameters");
        parameters.put("dimension", dimensions);
        return objectMapper.writeValueAsString(root);
    }

    private List<Embedding> embeddings(JsonNode root) {
        JsonNode items = root.path("output").path("embeddings");
        if (!items.isArray()) {
            throw new IllegalStateException("DashScope embedding response did not include output.embeddings");
        }

        List<Embedding> embeddings = new ArrayList<>();
        for (JsonNode item : items) {
            JsonNode vector = item.path("embedding");
            if (!vector.isArray()) {
                continue;
            }
            float[] values = new float[vector.size()];
            for (int i = 0; i < vector.size(); i++) {
                values[i] = (float) vector.get(i).asDouble();
            }
            embeddings.add(Embedding.from(values));
        }
        if (embeddings.isEmpty()) {
            throw new IllegalStateException("DashScope embedding response contained no vectors");
        }
        return embeddings;
    }

    private String errorMessage(JsonNode root, int statusCode) {
        String message = root.path("message").asText("");
        if (message.isBlank()) {
            message = root.path("error").path("message").asText("");
        }
        if (message.isBlank()) {
            message = "HTTP " + statusCode;
        }
        return "DashScope embedding request failed: " + message;
    }

    private URI nativeEndpoint(String baseUrl) {
        URI uri = URI.create(baseUrl);
        String scheme = uri.getScheme() == null ? "https" : uri.getScheme();
        String authority = uri.getRawAuthority();
        if (authority == null || authority.isBlank()) {
            throw new IllegalArgumentException("Invalid DashScope base URL: " + baseUrl);
        }
        return URI.create(scheme + "://" + authority + MULTIMODAL_EMBEDDING_PATH);
    }
}
