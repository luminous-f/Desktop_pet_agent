package com.desktoppet.news;

import com.desktoppet.config.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public final class OpenWebSearchService implements SearchService {
    private final AppConfig config;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenWebSearchService(AppConfig config) {
        this.config = config;
    }

    @Override
    public String search(String query) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("query", query);
            payload.put("limit", 5);

            HttpRequest request = HttpRequest.newBuilder(URI.create(config.searchBaseUrl()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return "搜索失败：Open-WebSearch 返回 HTTP " + response.statusCode() + "。";
            }
            return formatResults(objectMapper.readTree(response.body()));
        } catch (ConnectException e) {
            return "搜索失败：Open-WebSearch 未启动。请先运行 open-websearch serve。";
        } catch (Exception e) {
            return "搜索失败：" + e.getMessage();
        }
    }

    private String formatResults(JsonNode root) {
        List<JsonNode> results = resultNodes(root);
        if (results.isEmpty()) {
            return "搜索摘要：未找到结果。";
        }

        StringBuilder text = new StringBuilder("搜索结果：\n");
        int count = Math.min(results.size(), 5);
        for (int i = 0; i < count; i++) {
            JsonNode item = results.get(i);
            text.append("- ")
                    .append(firstText(item, "title", "name", "text", "description"))
                    .append(" ")
                    .append(firstText(item, "url", "link", "href"))
                    .append("\n");
            String summary = firstText(item, "description", "snippet", "content", "summary");
            if (!summary.isBlank()) {
                text.append("  ").append(summary).append("\n");
            }
        }
        return text.toString();
    }

    private List<JsonNode> resultNodes(JsonNode root) {
        JsonNode candidates = resultArray(root);

        List<JsonNode> nodes = new ArrayList<>();
        if (candidates.isArray()) {
            candidates.forEach(nodes::add);
        }
        return nodes;
    }

    private JsonNode resultArray(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return objectMapper.createArrayNode();
        }
        if (node.isArray()) {
            return node;
        }
        if (!node.isObject()) {
            return objectMapper.createArrayNode();
        }
        for (String field : List.of("results", "items")) {
            JsonNode value = node.path(field);
            if (value.isArray()) {
                return value;
            }
        }
        JsonNode data = node.path("data");
        if (data.isObject() || data.isArray()) {
            return resultArray(data);
        }
        return objectMapper.createArrayNode();
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText("");
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
