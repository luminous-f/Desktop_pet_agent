package com.desktoppet.service.impl;

import com.desktoppet.config.AppConfig;
import com.desktoppet.dao.ConversationMapper;
import com.desktoppet.dao.LongTermMemoryMapper;
import com.desktoppet.dao.SessionSummaryMapper;
import com.desktoppet.entity.LongTermMemoryRecord;
import com.desktoppet.entity.SessionSummaryRecord;
import com.desktoppet.memory.InMemoryConversationMemory;
import com.desktoppet.service.ConversationMemoryService;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConversationMemoryServiceImpl implements ConversationMemoryService {
    private static final int DEFAULT_KEEP_RECENT_MESSAGES = 8;

    private final ConversationMapper conversationMapper;
    private final LongTermMemoryMapper longTermMemoryMapper;
    private final SessionSummaryMapper sessionSummaryMapper;
    private final InMemoryConversationMemory shortTermMemory;
    private final int contextBudgetTokens;

    public ConversationMemoryServiceImpl(
            ConversationMapper conversationMapper,
            LongTermMemoryMapper longTermMemoryMapper,
            SessionSummaryMapper sessionSummaryMapper,
            AppConfig config
    ) {
        this.conversationMapper = conversationMapper;
        this.longTermMemoryMapper = longTermMemoryMapper;
        this.sessionSummaryMapper = sessionSummaryMapper;
        this.shortTermMemory = new InMemoryConversationMemory(32);
        this.contextBudgetTokens = config.contextBudgetTokens();
    }

    @Override
    public void recordRaw(String sessionId, String role, String content) {
        conversationMapper.insert(sessionId, role, content);
        shortTermMemory.add(role, content);
    }

    @Override
    public List<String> shortTermSnapshot() {
        return shortTermMemory.snapshot();
    }

    @Override
    public List<String> weightedLongTermMemories(int limit) {
        List<LongTermMemoryRecord> memories = longTermMemoryMapper.selectWeighted(limit);
        String idsCsv = memories.stream()
                .map(LongTermMemoryRecord::getId)
                .map(String::valueOf)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        if (!idsCsv.isBlank()) {
            longTermMemoryMapper.markAccessed(idsCsv);
        }
        return memories.stream().map(LongTermMemoryRecord::getContent).toList();
    }

    @Override
    public List<String> recentSessionSummaries(int limit) {
        try {
            return sessionSummaryMapper.selectRecentWeighted(limit).stream()
                    .map(SessionSummaryRecord::getSummaryText)
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    @Override
    public void maybeRememberUserFact(String userMessage) {
        String normalized = userMessage.trim();
        if (normalized.length() < 4 || normalized.length() > 300) {
            return;
        }
        boolean shouldRemember = normalized.contains("记住")
                || normalized.contains("我喜欢")
                || normalized.contains("我不喜欢")
                || normalized.contains("我叫")
                || normalized.contains("我的")
                || normalized.contains("我正在");
        if (!shouldRemember) {
            return;
        }
        try {
            longTermMemoryMapper.insert("user_fact", normalized, normalized.contains("记住") ? 10 : 3);
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean compressIfNeeded(String sessionId, OpenAiChatModel model, String reason, int currentPromptTokens) {
        if (shortTermMemory.size() <= DEFAULT_KEEP_RECENT_MESSAGES && currentPromptTokens < contextBudgetTokens) {
            return false;
        }
        if (currentPromptTokens < contextBudgetTokens && shortTermMemory.size() < 20) {
            return false;
        }
        List<String> oldMessages = shortTermMemory.drainOldest(DEFAULT_KEEP_RECENT_MESSAGES);
        saveSummary(sessionId, oldMessages, model, reason);
        return true;
    }

    @Override
    public void summarizeOnShutdown(String sessionId, OpenAiChatModel model) {
        List<String> messages = shortTermMemory.snapshot();
        if (messages.isEmpty()) {
            return;
        }
        saveSummary(sessionId, messages, null, "shutdown");
        shortTermMemory.clear();
    }

    @Override
    public int contextBudgetTokens() {
        return contextBudgetTokens;
    }

    @Override
    public int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, text.length() / 2);
    }

    private void saveSummary(String sessionId, List<String> messages, OpenAiChatModel model, String reason) {
        if (messages.isEmpty()) {
            return;
        }
        String joined = String.join("\n", messages);
        String summary = summarize(joined, model, reason);
        int importance = joined.contains("记住") ? 8 : 4;
        try {
            sessionSummaryMapper.insert(sessionId, summary, messages.size(), importance);
        } catch (Exception ignored) {
        }
        extractStableFacts(joined);
    }

    private String summarize(String text, OpenAiChatModel model, String reason) {
        if (model == null) {
            return "会话摘要（" + reason + "）：\n" + truncate(text, 1200);
        }
        String prompt = """
                请把下面这段桌宠会话压缩成中文摘要，用于下次对话恢复上下文。
                只保留用户目标、偏好、正在做的事、未完成事项、重要情绪和明确要求。
                控制在 500 字以内。

                会话：
                %s
                """.formatted(text);
        try {
            return model.chat(prompt);
        } catch (RuntimeException e) {
            return "会话摘要（" + reason + "，模型摘要失败，已本地降级）：\n" + truncate(text, 1200);
        }
    }

    private void extractStableFacts(String text) {
        for (String line : text.split("\\n")) {
            String content = line.replaceFirst("^(user|assistant):\\s*", "").trim();
            if (content.contains("记住") || content.contains("我喜欢") || content.contains("我不喜欢") || content.contains("我正在")) {
                longTermMemoryMapper.insert("extracted_fact", truncate(content, 300), content.contains("记住") ? 9 : 4);
            }
        }
    }

    private String truncate(String text, int maxChars) {
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + "...";
    }
}
