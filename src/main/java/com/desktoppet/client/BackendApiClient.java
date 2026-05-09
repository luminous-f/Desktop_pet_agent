package com.desktoppet.client;

import com.desktoppet.controller.dto.ApiModels.AllowedRootsRequest;
import com.desktoppet.controller.dto.ApiModels.ChatRequest;
import com.desktoppet.controller.dto.ApiModels.ChatResponse;
import com.desktoppet.controller.dto.ApiModels.ErrorResponse;
import com.desktoppet.controller.dto.ApiModels.FileOrganizeConfirmRequest;
import com.desktoppet.controller.dto.ApiModels.FileOrganizePreviewRequest;
import com.desktoppet.controller.dto.ApiModels.FileOrganizeRequest;
import com.desktoppet.controller.dto.ApiModels.FileOrganizeResponse;
import com.desktoppet.controller.dto.ApiModels.NewsResponse;
import com.desktoppet.controller.dto.ApiModels.ProfileRequest;
import com.desktoppet.controller.dto.ApiModels.ProfileResponse;
import com.desktoppet.controller.dto.ApiModels.ScheduleItemResponse;
import com.desktoppet.controller.dto.ApiModels.ScheduleRequest;
import com.desktoppet.controller.dto.ApiModels.StartupResponse;
import com.desktoppet.files.FileOrganizer;
import com.desktoppet.schedule.ScheduleItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

public final class BackendApiClient {
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public BackendApiClient(String baseUrl) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public StartupResponse startup() {
        return get("/api/startup", StartupResponse.class);
    }

    public ChatResponse chat(String message) {
        return send("POST", "/api/chat", new ChatRequest(message), ChatResponse.class);
    }

    public String profile() {
        return get("/api/profile", ProfileResponse.class).profileText();
    }

    public String saveProfile(String profileText) {
        return send("PUT", "/api/profile", new ProfileRequest(profileText), ProfileResponse.class).profileText();
    }

    public List<ScheduleItem> todaySchedule() {
        return get("/api/schedule/today", new TypeReference<List<ScheduleItemResponse>>() {
        }).stream().map(this::toScheduleItem).toList();
    }

    public List<ScheduleItem> addTodaySchedule(String title) {
        return send("POST", "/api/schedule/today", new ScheduleRequest(title), new TypeReference<List<ScheduleItemResponse>>() {
        }).stream().map(this::toScheduleItem).toList();
    }

    public String dailyNews() {
        return get("/api/news/daily", NewsResponse.class).summary();
    }

    public String organizeFiles(String sourceRoot, String extension) {
        return send("POST", "/api/files/organize", new FileOrganizeRequest(sourceRoot, extension), FileOrganizeResponse.class).result();
    }

    public FileOrganizer.PreviewResult previewFileOrganization(String sourceRoot, String extensions, String instruction) {
        return send(
                "POST",
                "/api/files/organize/preview",
                new FileOrganizePreviewRequest(sourceRoot, extensions, instruction),
                FileOrganizer.PreviewResult.class
        );
    }

    public FileOrganizer.ConfirmResult confirmFileOrganization(String previewId) {
        return send(
                "POST",
                "/api/files/organize/confirm",
                new FileOrganizeConfirmRequest(previewId),
                FileOrganizer.ConfirmResult.class
        );
    }

    public FileOrganizer.AllowedRootsResult allowedRoots() {
        return get("/api/files/allowed-roots", FileOrganizer.AllowedRootsResult.class);
    }

    public FileOrganizer.AllowedRootsResult replaceAllowedRoots(List<String> roots) {
        return send("PUT", "/api/files/allowed-roots", new AllowedRootsRequest(roots), FileOrganizer.AllowedRootsResult.class);
    }

    private ScheduleItem toScheduleItem(ScheduleItemResponse response) {
        return new ScheduleItem(response.id(), LocalDate.parse(response.date()), response.title(), response.done());
    }

    private <T> T get(String path, Class<T> responseType) {
        return exchange(HttpRequest.newBuilder(uri(path)).GET().build(), responseType);
    }

    private <T> T get(String path, TypeReference<T> responseType) {
        return exchange(HttpRequest.newBuilder(uri(path)).GET().build(), responseType);
    }

    private <T> T send(String method, String path, Object body, Class<T> responseType) {
        return exchange(buildRequest(method, path, body), responseType);
    }

    private <T> T send(String method, String path, Object body, TypeReference<T> responseType) {
        return exchange(buildRequest(method, path, body), responseType);
    }

    private HttpRequest buildRequest(String method, String path, Object body) {
        try {
            return HttpRequest.newBuilder(uri(path))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
        } catch (IOException e) {
            throw new ApiException("请求序列化失败：" + e.getMessage(), e);
        }
    }

    private <T> T exchange(HttpRequest request, Class<T> responseType) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ensureSuccess(response);
            return mapper.readValue(response.body(), responseType);
        } catch (IOException e) {
            throw new ApiException("无法连接后端：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("请求被中断", e);
        }
    }

    private <T> T exchange(HttpRequest request, TypeReference<T> responseType) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ensureSuccess(response);
            return mapper.readValue(response.body(), responseType);
        } catch (IOException e) {
            throw new ApiException("无法连接后端：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("请求被中断", e);
        }
    }

    private void ensureSuccess(HttpResponse<String> response) throws IOException {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return;
        }
        ErrorResponse error = mapper.readValue(response.body(), ErrorResponse.class);
        throw new ApiException(error.error() == null || error.error().isBlank() ? "后端请求失败：" + response.statusCode() : error.error());
    }

    private URI uri(String path) {
        return URI.create(baseUrl + path);
    }

    private String trimTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    public static final class ApiException extends RuntimeException {
        public ApiException(String message) {
            super(message);
        }

        public ApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
