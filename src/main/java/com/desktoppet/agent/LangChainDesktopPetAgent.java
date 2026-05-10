package com.desktoppet.agent;

import com.desktoppet.agent.skill.AgentRouter;
import com.desktoppet.agent.skill.RouteDecision;
import com.desktoppet.agent.skill.SkillRegistry;
import com.desktoppet.agent.ai.LoreQuestionClassifierAiService;
import com.desktoppet.agent.ai.LoreQuestionDecision;
import com.desktoppet.agent.ai.LoreRetrievalQueryRewriteAiService;
import com.desktoppet.agent.ai.ProfileExtractionAiService;
import com.desktoppet.agent.ai.ProfileUpdateDecision;
import com.desktoppet.config.AppConfig;
import com.desktoppet.service.ConversationMemoryService;
import com.desktoppet.service.NewsService;
import com.desktoppet.service.SearchService;
import com.desktoppet.service.ProfileService;
import com.desktoppet.rag.RagService;
import com.desktoppet.resources.CharacterContextService;
import com.desktoppet.service.ScheduleService;
import com.desktoppet.service.WeatherService;
import com.desktoppet.service.FileService;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public final class LangChainDesktopPetAgent implements DesktopPetAgent {
    private static final Logger log = LoggerFactory.getLogger(LangChainDesktopPetAgent.class);
    private static final List<String> CHARACTER_RETRIEVAL_ALIASES = List.of("遐蝶", "小蝶", "Castorice");
    private static final List<String> CHARACTER_REFERENCE_MARKERS = List.of("遐蝶", "小蝶", "Castorice", "castorice", "桌宠");

    private final String sessionId = UUID.randomUUID().toString();
    private final ConversationMemoryService memoryService;
    private final ProfileService profileService;
    private final RagService ragService;
    private final String characterContext;
    private final String startupNotice;
    private final OpenAiChatModel model;
    private final ProfileExtractionAiService profileExtractionAiService;
    private final LoreQuestionClassifierAiService loreQuestionClassifierAiService;
    private final LoreRetrievalQueryRewriteAiService loreRetrievalQueryRewriteAiService;
    private final AgentRouter router;
    private final SkillRegistry skillRegistry;

    public LangChainDesktopPetAgent(
            AppConfig config,
            ConversationMemoryService memoryService,
            WeatherService weatherService,
            SearchService searchService,
            ScheduleService scheduleService,
            NewsService newsService,
            ProfileService profileService,
            FileService fileService,
            RagService ragService,
            CharacterContextService characterContextService
    ) {
        this.memoryService = memoryService;
        this.profileService = profileService;
        this.ragService = ragService;
        String loadedCharacterContext = characterContextService.loadDefaultContext();
        this.characterContext = loadedCharacterContext == null ? "" : loadedCharacterContext;
        int characterTokens = memoryService.estimateTokens(this.characterContext);
        this.startupNotice = characterTokens > config.contextBudgetTokens() / 3
                ? "角色资源较大，估算约 " + characterTokens + " tokens，已超过总上下文预算的三分之一，建议后续拆分或改为 RAG 注入。"
                : "";
        if (config.deepSeekApiKey().isBlank() || "replace-me".equals(config.deepSeekApiKey())) {
            this.model = null;
            this.profileExtractionAiService = null;
            this.loreQuestionClassifierAiService = null;
            this.loreRetrievalQueryRewriteAiService = null;
        } else {
            this.model = OpenAiChatModel.builder()
                    .baseUrl(config.deepSeekBaseUrl())
                    .apiKey(config.deepSeekApiKey())
                    .modelName(config.deepSeekChatModel())
                    .timeout(Duration.ofSeconds(45))
                    .build();
            this.profileExtractionAiService = AiServices.create(ProfileExtractionAiService.class, this.model);
            this.loreQuestionClassifierAiService = AiServices.create(LoreQuestionClassifierAiService.class, this.model);
            this.loreRetrievalQueryRewriteAiService = AiServices.create(LoreRetrievalQueryRewriteAiService.class, this.model);
        }
        this.router = new AgentRouter(model);
        this.skillRegistry = new SkillRegistry(
                weatherService,
                scheduleService,
                newsService,
                searchService,
                fileService,
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
        LoreQuestionDecision loreDecision = classifyLoreQuestion(userMessage);
        List<String> ragReferences = List.of();
        String retrievalQuery = userMessage;
        if (loreDecision.loreRelated()) {
            retrievalQuery = rewriteLoreRetrievalQuery(userMessage);
            ragReferences = ragService.retrieve(retrievalQuery);
        }
        log.info(
                "Lore classification: loreRelated={}, category={}, ragHit={}, ragReferences={}, retrievalQuery={}",
                loreDecision.loreRelated(),
                loreDecision.category(),
                !ragReferences.isEmpty(),
                ragReferences.size(),
                retrievalQuery
        );
        String prompt = buildPrompt(userMessage, null, loreDecision, retrievalQuery, ragReferences);
        if (memoryService.compressIfNeeded(sessionId, model, "context-budget", memoryService.estimateTokens(prompt))) {
            prompt = buildPrompt(userMessage, null, loreDecision, retrievalQuery, ragReferences);
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
    @PreDestroy
    public void shutdown() {
        memoryService.summarizeOnShutdown(sessionId, model);
    }

    private String buildPrompt(
            String userMessage,
            String toolResult,
            LoreQuestionDecision loreDecision,
            String retrievalQuery,
            List<String> ragReferences
    ) {
        boolean loreRelated = loreDecision != null && loreDecision.loreRelated();
        boolean ragHit = ragReferences != null && !ragReferences.isEmpty();
        return """
                Role and style:
                You are a small desktop companion. Reply in Chinese unless the user asks otherwise.
                Keep answers concise and useful. Address the user as “阁下” when it is natural.
                Use the character context for tone and roleplay, but never claim game facts as real-world facts.
                If tool data is provided, use it as ground truth.
                If Lore question status says loreRelated=true, answer using only Character context and Retrieved local references as factual sources.
                If loreRelated=true and ragHit=false, do not answer from imagination or general model knowledge; briefly say in character that the current records do not mention it and you are not sure.
                If loreRelated=true and ragHit=true, use the retrieved references, and say you are not sure for facts not present in them.
                Do not invent plot, relationships, motives, events, names, chronology, or canon setting.
                Output only spoken dialogue. Do not include action descriptions, facial expressions, stage directions, narration, or bracketed roleplay.
                Address the user only as “阁下”. Never call the user “主人”.

                Character context:
                %s

                Long-term memories:
                %s

                Recent session summaries:
                %s

                Lore question status:
                loreRelated=%s
                category=%s
                ragHit=%s
                classifierReason=%s
                retrievalQuery=%s

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
                loreRelated,
                loreDecision == null || loreDecision.category() == null ? "none" : loreDecision.category(),
                ragHit,
                loreDecision == null || loreDecision.reason() == null ? "none" : loreDecision.reason(),
                retrievalQuery == null || retrievalQuery.isBlank() ? userMessage : retrievalQuery,
                ragHit ? String.join("\n\n", ragReferences) : "none",
                profileService.currentProfile(),
                String.join("\n", memoryService.shortTermSnapshot()),
                toolResult == null ? "none" : toolResult,
                userMessage
        );
    }

    private LoreQuestionDecision classifyLoreQuestion(String userMessage) {
        if (loreQuestionClassifierAiService == null || userMessage == null || userMessage.isBlank()) {
            return LoreQuestionDecision.notLore("classifier unavailable");
        }
        try {
            LoreQuestionDecision decision = loreQuestionClassifierAiService.classify(
                    profileService.currentProfile().isBlank() ? "none" : profileService.currentProfile(),
                    userMessage
            );
            if (decision == null) {
                log.warn("Lore classification returned empty result.");
                return LoreQuestionDecision.notLore("empty classifier result");
            }
            return normalizeLoreDecision(decision);
        } catch (Exception e) {
            log.warn("Lore classification failed: {}", e.getMessage());
            return LoreQuestionDecision.notLore("classifier failed");
        }
    }

    private LoreQuestionDecision normalizeLoreDecision(LoreQuestionDecision decision) {
        if (decision.loreRelated()) {
            return decision;
        }
        String category = decision.category() == null ? "" : decision.category().toLowerCase(Locale.ROOT);
        boolean loreCategory = List.of(
                "story", "lore", "character", "relationship", "world", "worldbuilding",
                "background", "plot", "canon", "dialogue", "motive", "chronology",
                "剧情", "设定", "角色", "人物", "关系", "世界观", "背景", "台词"
        ).stream().anyMatch(category::contains);
        if (!loreCategory) {
            return decision;
        }
        return new LoreQuestionDecision(true, decision.category(), "normalized from lore category: " + decision.reason());
    }

    private String rewriteLoreRetrievalQuery(String userMessage) {
        if (loreRetrievalQueryRewriteAiService == null || userMessage == null || userMessage.isBlank()) {
            return enrichCharacterAliasesForRetrieval(userMessage, userMessage);
        }
        try {
            String rewritten = loreRetrievalQueryRewriteAiService.rewrite(
                    profileService.currentProfile().isBlank() ? "none" : profileService.currentProfile(),
                    characterContext.isBlank() ? "none" : characterContext,
                    userMessage
            );
            String normalized = rewritten == null ? "" : rewritten.trim();
            if (normalized.isBlank()) {
                return enrichCharacterAliasesForRetrieval(userMessage, userMessage);
            }
            normalized = enrichCharacterAliasesForRetrieval(userMessage, normalized);
            log.info("Lore retrieval query rewritten: original={}, rewritten={}", userMessage, normalized);
            return normalized;
        } catch (Exception e) {
            log.warn("Lore retrieval query rewrite failed: {}", e.getMessage());
            return enrichCharacterAliasesForRetrieval(userMessage, userMessage);
        }
    }

    private String enrichCharacterAliasesForRetrieval(String userMessage, String retrievalQuery) {
        String query = retrievalQuery == null ? "" : retrievalQuery.trim();
        if (query.isBlank()) {
            query = userMessage == null ? "" : userMessage.trim();
        }
        String combined = ((userMessage == null ? "" : userMessage) + "\n" + query).toLowerCase(Locale.ROOT);
        boolean referencesCharacter = CHARACTER_REFERENCE_MARKERS.stream()
                .map(marker -> marker.toLowerCase(Locale.ROOT))
                .anyMatch(combined::contains);
        if (!referencesCharacter) {
            return query;
        }
        StringBuilder enriched = new StringBuilder(query);
        for (String alias : CHARACTER_RETRIEVAL_ALIASES) {
            if (!containsIgnoreCase(query, alias)) {
                if (!enriched.isEmpty()) {
                    enriched.append(' ');
                }
                enriched.append(alias);
            }
        }
        return enriched.toString();
    }

    private boolean containsIgnoreCase(String text, String value) {
        if (text == null || value == null) {
            return false;
        }
        return text.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT));
    }

    private void maybeUpdateProfile(String userMessage) {
        if (model == null || userMessage == null || userMessage.isBlank()) {
            return;
        }
        String currentProfile = profileService.currentProfile();
        try {
            ProfileUpdateDecision decision = profileExtractionAiService.extract(
                    currentProfile.isBlank() ? "无" : currentProfile,
                    userMessage
            );
            if (decision != null && decision.shouldUpdate()) {
                String profileText = decision.profileText() == null ? "" : decision.profileText().trim();
                if (!profileText.isBlank() && !profileText.equals(currentProfile)) {
                    profileService.saveProfile(profileText);
                }
            }
        } catch (Exception ignored) {
        }
    }
}
