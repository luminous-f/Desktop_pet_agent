package com.desktoppet.agent;

import com.desktoppet.agent.skill.AgentRouter;
import com.desktoppet.agent.skill.RouteDecision;
import com.desktoppet.agent.skill.SkillRegistry;
import com.desktoppet.config.AppConfig;
import com.desktoppet.files.FileOrganizer;
import com.desktoppet.memory.ConversationMemoryService;
import com.desktoppet.news.NewsService;
import com.desktoppet.news.SearchService;
import com.desktoppet.profile.UserProfileService;
import com.desktoppet.rag.RagService;
import com.desktoppet.schedule.ScheduleService;
import com.desktoppet.weather.QWeatherService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.time.Duration;
import java.util.UUID;

public final class LangChainDesktopPetAgent implements DesktopPetAgent {
    private final String sessionId = UUID.randomUUID().toString();
    private final ConversationMemoryService memoryService;
    private final UserProfileService profileService;
    private final RagService ragService;
    private final String characterContext;
    private final String startupNotice;
    private final OpenAiChatModel model;
    private final AgentRouter router;
    private final SkillRegistry skillRegistry;
    private final ObjectMapper mapper = new ObjectMapper();

    public LangChainDesktopPetAgent(
            AppConfig config,
            ConversationMemoryService memoryService,
            QWeatherService weatherService,
            SearchService searchService,
            ScheduleService scheduleService,
            NewsService newsService,
            UserProfileService profileService,
            FileOrganizer fileOrganizer,
            RagService ragService,
            String characterContext
    ) {
        this.memoryService = memoryService;
        this.profileService = profileService;
        this.ragService = ragService;
        this.characterContext = characterContext == null ? "" : characterContext;
        int characterTokens = memoryService.estimateTokens(this.characterContext);
        this.startupNotice = characterTokens > config.contextBudgetTokens() / 3
                ? "角色资源较大，估算约 " + characterTokens + " tokens，已超过总上下文预算的三分之一，建议后续拆分或改为 RAG 注入。"
                : "";
        if (config.deepSeekApiKey().isBlank() || "replace-me".equals(config.deepSeekApiKey())) {
            this.model = null;
        } else {
            this.model = OpenAiChatModel.builder()
                    .baseUrl(config.deepSeekBaseUrl())
                    .apiKey(config.deepSeekApiKey())
                    .modelName(config.deepSeekChatModel())
                    .timeout(Duration.ofSeconds(45))
                    .build();
        }
        this.router = new AgentRouter(model);
        this.skillRegistry = new SkillRegistry(
                weatherService,
                scheduleService,
                newsService,
                searchService,
                fileOrganizer,
                profileService,
                model
        );
    }

    @Override
    public String reply(String userMessage) {
        memoryService.recordRaw(sessionId, "user", userMessage);
        memoryService.maybeRememberUserFact(userMessage);
        maybeUpdateProfile(userMessage);

        if (model == null) {
            String answer = "DeepSeek API 尚未配置。需要 DEEPSEEK_API_KEY、DEEPSEEK_BASE_URL 和 DEEPSEEK_CHAT_MODEL。";
            memoryService.recordRaw(sessionId, "assistant", answer);
            return answer;
        }
        RouteDecision decision = router.route(userMessage, skillRegistry.pendingState());
        String skillResult;
        try {
            skillResult = skillRegistry.execute(decision, userMessage);
        } catch (Exception e) {
            skillResult = "工具调用失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        if (skillResult != null) {
            memoryService.recordRaw(sessionId, "assistant", skillResult);
            return skillResult;
        }
        String prompt = buildPrompt(userMessage, null);
        if (memoryService.compressIfNeeded(sessionId, model, "context-budget", memoryService.estimateTokens(prompt))) {
            prompt = buildPrompt(userMessage, null);
        }
        String answer = model.chat(prompt);

        memoryService.recordRaw(sessionId, "assistant", answer);
        return answer;
    }

    @Override
    public String proactiveTopic() {
        if (model == null) {
            return "记得喝水，也可以伸展一下肩颈。";
        }
        String profile = profileService.currentProfile();
        String prompt = """
                You are a desktop pet. Generate one short proactive message in Chinese.
                It can remind the user to drink water, stretch, check schedule, or ask about a recent interest.
                Avoid pretending to know real-time facts.
                Output only spoken dialogue. Do not include action descriptions, facial expressions, stage directions, narration, or bracketed roleplay.
                Address the user only as “阁下”. Never call the user “主人”.
                User profile:
                %s
                """.formatted(profile);
        return model.chat(prompt);
    }

    @Override
    public String startupNotice() {
        return startupNotice;
    }

    @Override
    public void shutdown() {
        memoryService.summarizeOnShutdown(sessionId, model);
    }

    private String buildPrompt(String userMessage, String toolResult) {
        return """
                Role and style:
                You are a small desktop companion. Reply in Chinese unless the user asks otherwise.
                Keep answers concise and useful. Address the user as “阁下” when it is natural.
                Use the character context for tone and roleplay, but never claim game facts as real-world facts.
                If tool data is provided, use it as ground truth.
                Output only spoken dialogue. Do not include action descriptions, facial expressions, stage directions, narration, or bracketed roleplay.
                Address the user only as “阁下”. Never call the user “主人”.

                Character context:
                %s

                Long-term memories:
                %s

                Recent session summaries:
                %s

                Retrieved local references:
                %s

                User profile:
                %s

                Recent conversation:
                %s

                Tool result:
                %s

                User message:
                %s
                """.formatted(
                characterContext.isBlank() ? "none" : characterContext,
                String.join("\n", memoryService.weightedLongTermMemories(12)),
                String.join("\n\n", memoryService.recentSessionSummaries(4)),
                String.join("\n\n", ragService.retrieve(userMessage)),
                profileService.currentProfile(),
                String.join("\n", memoryService.shortTermSnapshot()),
                toolResult == null ? "none" : toolResult,
                userMessage
        );
    }

    private void maybeUpdateProfile(String userMessage) {
        if (model == null || userMessage == null || userMessage.isBlank()) {
            return;
        }
        String currentProfile = profileService.currentProfile();
        String prompt = """
                判断用户消息是否包含稳定画像信息，例如身份、长期偏好、长期目标、正在持续进行的事、重要称呼偏好。
                不要记录临时情绪、一次性请求、工具执行状态、文件路径白名单、日程确认文本。
                如需更新，请基于当前画像生成完整的新画像文本；否则保持 shouldUpdate=false。
                只输出 JSON：
                {"shouldUpdate":true/false,"profileText":"完整画像文本","reason":"简短理由"}

                当前画像：
                %s

                用户消息：
                %s
                """.formatted(currentProfile.isBlank() ? "无" : currentProfile, userMessage);
        try {
            JsonNode json = parseJson(model.chat(prompt));
            if (json.path("shouldUpdate").asBoolean(false)) {
                String profileText = json.path("profileText").asText("").trim();
                if (!profileText.isBlank() && !profileText.equals(currentProfile)) {
                    profileService.saveProfile(profileText);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private JsonNode parseJson(String raw) throws Exception {
        String text = raw == null ? "" : raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            text = text.substring(start, end + 1);
        }
        return mapper.readTree(text);
    }
}
