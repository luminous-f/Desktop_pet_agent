package com.desktoppet.agent.skill;

import com.desktoppet.files.FileOrganizer;
import com.desktoppet.news.NewsService;
import com.desktoppet.news.SearchService;
import com.desktoppet.profile.UserProfileService;
import com.desktoppet.schedule.ScheduleItem;
import com.desktoppet.schedule.ScheduleService;
import com.desktoppet.weather.QWeatherService;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.util.List;
import java.util.Map;
import java.util.Arrays;

public final class SkillRegistry {
    private final QWeatherService weatherService;
    private final ScheduleService scheduleService;
    private final NewsService newsService;
    private final SearchService searchService;
    private final FileOrganizer fileOrganizer;
    private final UserProfileService profileService;
    private final OpenAiChatModel model;
    private String pendingScheduleTitle;
    private String pendingFilePreviewId;

    public SkillRegistry(
            QWeatherService weatherService,
            ScheduleService scheduleService,
            NewsService newsService,
            SearchService searchService,
            FileOrganizer fileOrganizer,
            UserProfileService profileService,
            OpenAiChatModel model
    ) {
        this.weatherService = weatherService;
        this.scheduleService = scheduleService;
        this.newsService = newsService;
        this.searchService = searchService;
        this.fileOrganizer = fileOrganizer;
        this.profileService = profileService;
        this.model = model;
    }

    public String pendingState() {
        StringBuilder builder = new StringBuilder();
        if (pendingScheduleTitle != null && !pendingScheduleTitle.isBlank()) {
            builder.append("pending schedule add title: ").append(pendingScheduleTitle).append("\n");
        }
        if (pendingFilePreviewId != null && !pendingFilePreviewId.isBlank()) {
            builder.append("pending file organization preview id: ").append(pendingFilePreviewId).append("\n");
        }
        return builder.isEmpty() ? "none" : builder.toString();
    }

    public String execute(RouteDecision decision, String userMessage) {
        if ("clarify".equalsIgnoreCase(decision.action())) {
            return decision.clarification().isBlank() ? "还需要补充信息后才能继续。" : decision.clarification();
        }
        if (!"skill".equalsIgnoreCase(decision.action())) {
            return null;
        }
        return switch (decision.skill()) {
            case "weather" -> weather(userMessage);
            case "news" -> news();
            case "search" -> search(decision.arguments(), userMessage);
            case "schedule" -> schedule(decision.arguments());
            case "file_organize" -> fileOrganize(decision.arguments());
            case "allowed_roots" -> allowedRoots(decision.arguments());
            case "profile" -> profile(decision.arguments());
            default -> null;
        };
    }

    private String weather(String userMessage) {
        String weather = weatherService.todayWeather();
        if (model == null) {
            return weather;
        }
        String prompt = """
                根据真实天气信息回答用户，必须使用天气信息为事实依据。
                如果用户问穿衣/出门建议，要给出简短穿衣建议。
                输出中文，简洁自然。

                用户问题：
                %s

                天气信息：
                %s
                """.formatted(userMessage, weather);
        return model.chat(prompt);
    }

    private String news() {
        return newsService.dailySummary();
    }

    private String search(Map<String, String> arguments, String userMessage) {
        String query = arguments.getOrDefault("query", "").trim();
        if (query.isBlank()) {
            query = userMessage;
        }
        return searchService.search(query);
    }

    private String schedule(Map<String, String> arguments) {
        String op = arguments.getOrDefault("op", "query_today");
        if ("confirm_add".equalsIgnoreCase(op)) {
            if (pendingScheduleTitle == null || pendingScheduleTitle.isBlank()) {
                return "没有待确认的新日程。";
            }
            scheduleService.addTodayItem(pendingScheduleTitle);
            String added = pendingScheduleTitle;
            pendingScheduleTitle = null;
            return "已添加今日日程：" + added;
        }
        if ("add".equalsIgnoreCase(op)) {
            String title = arguments.getOrDefault("title", "").trim();
            if (title.isBlank()) {
                return "要添加日程还缺少事项内容。";
            }
            pendingScheduleTitle = title;
            return "请确认是否添加今日日程：“" + title + "”。确认后我再写入日程。";
        }
        List<ScheduleItem> items = scheduleService.todayItems();
        if (items.isEmpty()) {
            return "今天还没有日程。";
        }
        StringBuilder builder = new StringBuilder("今日日程：\n");
        for (ScheduleItem item : items) {
            builder.append(item.done() ? "[x] " : "[ ] ").append(item.title()).append("\n");
        }
        return builder.toString();
    }

    private String fileOrganize(Map<String, String> arguments) {
        String op = arguments.getOrDefault("op", "preview");
        if ("confirm".equalsIgnoreCase(op)) {
            if (pendingFilePreviewId == null || pendingFilePreviewId.isBlank()) {
                return "没有待确认的文件整理预览。请先告诉我要整理的范围、文件类型和要求。";
            }
            FileOrganizer.ConfirmResult result = fileOrganizer.confirm(pendingFilePreviewId);
            pendingFilePreviewId = null;
            return fileOrganizer.summarizeConfirm(result);
        }
        String sourceRoot = arguments.getOrDefault("sourceRoot", "").trim();
        if (sourceRoot.isBlank()) {
            return "要整理文件还缺少扫描范围。请告诉我目录或盘符，例如“D盘”或“/mnt/d/papers”。";
        }
        String extensions = arguments.getOrDefault("extensions", "pdf").trim();
        String instruction = arguments.getOrDefault("instruction", "").trim();
        if (instruction.isBlank()) {
            instruction = "按用户要求分类文献";
        }
        FileOrganizer.PreviewResult preview = fileOrganizer.preview(new FileOrganizer.PreviewRequest(sourceRoot, extensions, instruction));
        pendingFilePreviewId = preview.previewId();
        return fileOrganizer.summarizePreview(preview);
    }

    private String allowedRoots(Map<String, String> arguments) {
        String roots = arguments.getOrDefault("roots", "").trim();
        if (roots.isBlank()) {
            return "没有识别到要设置的白名单路径。请说“把D盘加入白名单”或“设置文件白名单为D:\\papers”。";
        }
        List<String> rootList = Arrays.stream(roots.split("[;；]"))
                .map(String::trim)
                .filter(root -> !root.isEmpty())
                .toList();
        FileOrganizer.AllowedRootsResult result = fileOrganizer.replaceAllowedRoots(rootList);
        return fileOrganizer.summarizeAllowedRoots(result);
    }

    private String profile(Map<String, String> arguments) {
        String op = arguments.getOrDefault("op", "query");
        if ("replace".equalsIgnoreCase(op)) {
            String profileText = arguments.getOrDefault("profileText", "");
            profileService.saveProfile(profileText);
            return "用户画像已更新。";
        }
        String profile = profileService.currentProfile();
        return profile.isBlank() ? "当前还没有用户画像。" : "当前用户画像：\n" + profile;
    }
}
