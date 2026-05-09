package com.desktoppet.backend;

import java.util.List;

public final class ApiModels {
    private ApiModels() {
    }

    public record ChatRequest(String message) {
    }

    public record ChatResponse(String reply, String expression) {
    }

    public record ErrorResponse(String error) {
    }

    public record FileOrganizeRequest(String sourceRoot, String extension) {
    }

    public record FileOrganizeResponse(String result) {
    }

    public record FileOrganizePreviewRequest(String sourceRoot, String extensions, String instruction) {
    }

    public record FileOrganizeConfirmRequest(String previewId) {
    }

    public record AllowedRootsRequest(List<String> roots) {
    }

    public record NewsResponse(String summary) {
    }

    public record ProfileRequest(String profileText) {
    }

    public record ProfileResponse(String profileText) {
    }

    public record ScheduleItemResponse(long id, String date, String title, boolean done) {
    }

    public record ScheduleRequest(String title) {
    }

    public record StartupResponse(
            String startupNotice,
            String weather,
            String profileText,
            List<ScheduleItemResponse> todaySchedule
    ) {
    }
}
