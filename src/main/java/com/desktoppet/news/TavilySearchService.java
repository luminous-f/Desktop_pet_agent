package com.desktoppet.news;

import com.desktoppet.config.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public final class TavilySearchService implements SearchService {
    private final AppConfig config;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TavilySearchService(AppConfig config) {
        this.config = config;
    }

    @Override
    public String search(String query) {
        if (config.searchApiKey().isBlank() || "replace-me".equals(config.searchApiKey())) {
            return "搜索 API 尚未配置。需要 SEARCH_API_KEY。";
        }
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("query", query);
            payload.put("max_results", 5);
            payload.put("include_answer", true);

            HttpRequest request = HttpRequest.newBuilder(URI.create(config.searchBaseUrl()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.searchApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            StringBuilder result = new StringBuilder();
            result.append("搜索摘要：").append(root.path("answer").asText("无直接摘要")).append("\n来源：\n");
            for (JsonNode item : root.path("results")) {
                result.append("- ")
                        .append(item.path("title").asText("Untitled"))
                        .append(" ")
                        .append(item.path("url").asText(""))
                        .append("\n");
            }
            return result.toString();
        } catch (Exception e) {
            return "搜索失败：" + e.getMessage();
        }
    }
}
