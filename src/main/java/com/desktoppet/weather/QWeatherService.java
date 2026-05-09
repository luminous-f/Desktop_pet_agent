package com.desktoppet.weather;

import com.desktoppet.config.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

public final class QWeatherService {
    private final AppConfig config;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public QWeatherService(AppConfig config) {
        this.config = config;
    }

    public String todayWeather() {
        if (config.qWeatherApiKey().isBlank() || "replace-me".equals(config.qWeatherApiKey())) {
            return "天气 API 尚未配置。需要 WEATHER_API_KEY 和 QWEATHER_LOCATION。";
        }
        try {
            String location = URLEncoder.encode(config.qWeatherLocation(), StandardCharsets.UTF_8);
            String uri = config.qWeatherBaseUrl() + "/v7/weather/now?location=" + location + "&key=" + config.qWeatherApiKey();
            HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
                    .header("Accept-Encoding", "identity")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return "天气查询失败：QWeather 返回 HTTP " + response.statusCode() + "。";
            }
            JsonNode root = objectMapper.readTree(responseBody(response));
            String code = root.path("code").asText("");
            if (!"200".equals(code)) {
                return "天气查询失败：QWeather 返回状态码 " + (code.isBlank() ? "未知" : code) + "。请检查 WEATHER_API_KEY 和 QWEATHER_LOCATION。";
            }
            JsonNode now = root.path("now");
            if (now.isMissingNode() || now.isEmpty()) {
                return "天气查询失败：QWeather 响应中缺少实时天气数据。";
            }
            return "今日天气：%s，温度 %s°C，体感 %s°C，湿度 %s%%，风向 %s，风力 %s 级。".formatted(
                    now.path("text").asText("未知"),
                    now.path("temp").asText("?"),
                    now.path("feelsLike").asText("?"),
                    now.path("humidity").asText("?"),
                    now.path("windDir").asText("未知"),
                    now.path("windScale").asText("?")
            );
        } catch (Exception e) {
            return "天气查询失败：" + e.getMessage();
        }
    }

    private String responseBody(HttpResponse<byte[]> response) throws Exception {
        String encoding = response.headers()
                .firstValue("Content-Encoding")
                .orElse("")
                .toLowerCase(Locale.ROOT);
        byte[] body = response.body();
        if (encoding.contains("gzip") || isGzip(body)) {
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(body))) {
                return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    private boolean isGzip(byte[] body) {
        return body != null
                && body.length >= 2
                && (body[0] & 0xff) == 0x1f
                && (body[1] & 0xff) == 0x8b;
    }
}
