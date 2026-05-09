package com.desktoppet.backend;

import com.desktoppet.backend.ApiModels.AllowedRootsRequest;
import com.desktoppet.backend.ApiModels.ChatRequest;
import com.desktoppet.backend.ApiModels.ChatResponse;
import com.desktoppet.backend.ApiModels.ErrorResponse;
import com.desktoppet.backend.ApiModels.FileOrganizeConfirmRequest;
import com.desktoppet.backend.ApiModels.FileOrganizePreviewRequest;
import com.desktoppet.backend.ApiModels.FileOrganizeRequest;
import com.desktoppet.backend.ApiModels.FileOrganizeResponse;
import com.desktoppet.backend.ApiModels.NewsResponse;
import com.desktoppet.backend.ApiModels.ProfileRequest;
import com.desktoppet.backend.ApiModels.ProfileResponse;
import com.desktoppet.backend.ApiModels.ScheduleItemResponse;
import com.desktoppet.backend.ApiModels.ScheduleRequest;
import com.desktoppet.backend.ApiModels.StartupResponse;
import com.desktoppet.files.FileOrganizer;
import com.desktoppet.schedule.ScheduleItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;

public final class BackendHttpServer {
    private final BackendServices services;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpServer server;

    public BackendHttpServer(BackendServices services, String host, int port) throws IOException {
        this.services = services;
        this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
        this.server.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "desktop-pet-api");
            thread.setDaemon(true);
            return thread;
        }));
        registerRoutes();
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(1);
    }

    private void registerRoutes() {
        server.createContext("/api/startup", exchange -> handle(exchange, "GET", this::startup));
        server.createContext("/api/chat", exchange -> handle(exchange, "POST", this::chat));
        server.createContext("/api/profile", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                handle(exchange, "GET", this::profile);
            } else {
                handle(exchange, "PUT", this::saveProfile);
            }
        });
        server.createContext("/api/schedule/today", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                handle(exchange, "GET", this::todaySchedule);
            } else {
                handle(exchange, "POST", this::addTodaySchedule);
            }
        });
        server.createContext("/api/news/daily", exchange -> handle(exchange, "GET", this::dailyNews));
        server.createContext("/api/files/allowed-roots", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                handle(exchange, "GET", this::allowedRoots);
            } else {
                handle(exchange, "PUT", this::replaceAllowedRoots);
            }
        });
        server.createContext("/api/files/organize/preview", exchange -> handle(exchange, "POST", this::previewOrganizeFiles));
        server.createContext("/api/files/organize/confirm", exchange -> handle(exchange, "POST", this::confirmOrganizeFiles));
        server.createContext("/api/files/organize", exchange -> handle(exchange, "POST", this::organizeFiles));
    }

    private Object startup(HttpExchange exchange) {
        String startupNotice = services.agent().startupNotice();
        String weather = services.weatherService().todayWeather();
        String profileText = services.profileService().currentProfile();
        return new StartupResponse(startupNotice, weather, profileText, scheduleResponses(services.scheduleService().todayItems()));
    }

    private Object chat(HttpExchange exchange) throws IOException {
        ChatRequest request = read(exchange, ChatRequest.class);
        if (request.message() == null || request.message().isBlank()) {
            throw new BadRequestException("message 不能为空");
        }
        String reply = services.agent().reply(request.message().trim());
        return new ChatResponse(reply, "speaking");
    }

    private Object profile(HttpExchange exchange) {
        return new ProfileResponse(services.profileService().currentProfile());
    }

    private Object saveProfile(HttpExchange exchange) throws IOException {
        ProfileRequest request = read(exchange, ProfileRequest.class);
        String profileText = request.profileText() == null ? "" : request.profileText();
        services.profileService().saveProfile(profileText);
        return new ProfileResponse(services.profileService().currentProfile());
    }

    private Object todaySchedule(HttpExchange exchange) {
        return scheduleResponses(services.scheduleService().todayItems());
    }

    private Object addTodaySchedule(HttpExchange exchange) throws IOException {
        ScheduleRequest request = read(exchange, ScheduleRequest.class);
        if (request.title() == null || request.title().isBlank()) {
            throw new BadRequestException("title 不能为空");
        }
        services.scheduleService().addTodayItem(request.title().trim());
        return scheduleResponses(services.scheduleService().todayItems());
    }

    private Object dailyNews(HttpExchange exchange) {
        return new NewsResponse(services.newsService().dailySummary());
    }

    private Object allowedRoots(HttpExchange exchange) {
        return new FileOrganizer.AllowedRootsResult(services.fileOrganizer().allowedRoots());
    }

    private Object replaceAllowedRoots(HttpExchange exchange) throws IOException {
        AllowedRootsRequest request = read(exchange, AllowedRootsRequest.class);
        if (request.roots() == null) {
            throw new BadRequestException("roots 不能为空");
        }
        return services.fileOrganizer().replaceAllowedRoots(request.roots());
    }

    private Object organizeFiles(HttpExchange exchange) throws IOException {
        FileOrganizeRequest request = read(exchange, FileOrganizeRequest.class);
        if (request.sourceRoot() == null || request.sourceRoot().isBlank()) {
            throw new BadRequestException("sourceRoot 不能为空");
        }
        if (request.extension() == null || request.extension().isBlank()) {
            throw new BadRequestException("extension 不能为空");
        }
        FileOrganizer.PreviewResult preview = services.fileOrganizer().preview(
                new FileOrganizer.PreviewRequest(request.sourceRoot(), request.extension(), "按主题分类文献")
        );
        FileOrganizer.ConfirmResult result = services.fileOrganizer().confirm(preview.previewId());
        return new FileOrganizeResponse(services.fileOrganizer().summarizeConfirm(result));
    }

    private Object previewOrganizeFiles(HttpExchange exchange) throws IOException {
        FileOrganizePreviewRequest request = read(exchange, FileOrganizePreviewRequest.class);
        if (request.sourceRoot() == null || request.sourceRoot().isBlank()) {
            throw new BadRequestException("sourceRoot 不能为空");
        }
        return services.fileOrganizer().preview(new FileOrganizer.PreviewRequest(
                request.sourceRoot(),
                request.extensions(),
                request.instruction()
        ));
    }

    private Object confirmOrganizeFiles(HttpExchange exchange) throws IOException {
        FileOrganizeConfirmRequest request = read(exchange, FileOrganizeConfirmRequest.class);
        if (request.previewId() == null || request.previewId().isBlank()) {
            throw new BadRequestException("previewId 不能为空");
        }
        return services.fileOrganizer().confirm(request.previewId().trim());
    }

    private List<ScheduleItemResponse> scheduleResponses(List<ScheduleItem> items) {
        return items.stream()
                .map(item -> new ScheduleItemResponse(item.id(), item.date().toString(), item.title(), item.done()))
                .toList();
    }

    private <T> T read(HttpExchange exchange, Class<T> type) throws IOException {
        return mapper.readValue(exchange.getRequestBody(), type);
    }

    private void handle(HttpExchange exchange, String expectedMethod, Route route) throws IOException {
        try {
            addDefaultHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                send(exchange, 204, "");
                return;
            }
            if (!expectedMethod.equals(exchange.getRequestMethod())) {
                send(exchange, 405, mapper.writeValueAsString(new ErrorResponse("Method Not Allowed")));
                return;
            }
            Object response = route.handle(exchange);
            send(exchange, 200, mapper.writeValueAsString(response));
        } catch (BadRequestException e) {
            send(exchange, 400, mapper.writeValueAsString(new ErrorResponse(e.getMessage())));
        } catch (Exception e) {
            send(exchange, 500, mapper.writeValueAsString(new ErrorResponse(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())));
        } finally {
            exchange.close();
        }
    }

    private void addDefaultHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,PUT,OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private void send(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @FunctionalInterface
    private interface Route {
        Object handle(HttpExchange exchange) throws Exception;
    }

    private static final class BadRequestException extends RuntimeException {
        private BadRequestException(String message) {
            super(message);
        }
    }
}
