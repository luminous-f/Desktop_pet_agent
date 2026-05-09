package com.desktoppet.memory;

import com.desktoppet.config.AppConfig;
import com.desktoppet.storage.Database;
import com.desktoppet.storage.entity.LongTermMemoryRecord;
import com.desktoppet.storage.entity.SessionSummaryRecord;
import com.desktoppet.storage.mapper.LongTermMemoryMapper;
import com.desktoppet.storage.mapper.SessionSummaryMapper;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.apache.ibatis.session.SqlSession;

import java.util.List;

public final class ConversationMemoryService {
    private static final int DEFAULT_KEEP_RECENT_MESSAGES = 8;

    private final Database database;
    private final InMemoryConversationMemory shortTermMemory;
    private final int contextBudgetTokens;

    public ConversationMemoryService(Database database, InMemoryConversationMemory shortTermMemory, AppConfig config) {
        this.database = database;
        this.shortTermMemory = shortTermMemory;
        this.contextBudgetTokens = config.contextBudgetTokens();
    }

    public void recordRaw(String sessionId, String role, String content) {
        database.saveConversation(sessionId, role, content);
        shortTermMemory.add(role, content);
    }

    public List<String> shortTermSnapshot() {
        return shortTermMemory.snapshot();
    }

    public List<String> weightedLongTermMemories(int limit) {
        try (SqlSession session = database.openSession()) {
            LongTermMemoryMapper mapper = session.getMapper(LongTermMemoryMapper.class);
            List<LongTermMemoryRecord> memories = mapper.selectWeighted(limit);
            String idsCsv = memories.stream()
                    .map(LongTermMemoryRecord::getId)
                    .map(String::valueOf)
                    .reduce((left, right) -> left + "," + right)
                    .orElse("");
            if (!idsCsv.isBlank()) {
                mapper.markAccessed(idsCsv);
            }
            return memories.stream().map(LongTermMemoryRecord::getContent).toList();
        }
    }

    public List<String> recentSessionSummaries(int limit) {
        try (SqlSession session = database.openSession()) {
            return session.getMapper(SessionSummaryMapper.class).selectRecentWeighted(limit).stream()
                    .map(SessionSummaryRecord::getSummaryText)
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

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
            database.saveLongTermMemory("user_fact", normalized, normalized.contains("记住") ? 10 : 3);
        } catch (Exception ignored) {
        }
    }

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

    public void summarizeOnShutdown(String sessionId, OpenAiChatModel model) {
        List<String> messages = shortTermMemory.snapshot();
        if (messages.isEmpty()) {
            return;
        }
        saveSummary(sessionId, messages, model, "shutdown");
        shortTermMemory.clear();
    }

    public int contextBudgetTokens() {
        return contextBudgetTokens;
    }

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
        try (SqlSession session = database.openSession()) {
            session.getMapper(SessionSummaryMapper.class).insert(sessionId, summary, messages.size(), importance);
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
        return model.chat(prompt);
    }

    private void extractStableFacts(String text) {
        for (String line : text.split("\\n")) {
            String content = line.replaceFirst("^(user|assistant):\\s*", "").trim();
            if (content.contains("记住") || content.contains("我喜欢") || content.contains("我不喜欢") || content.contains("我正在")) {
                database.saveLongTermMemory("extracted_fact", truncate(content, 300), content.contains("记住") ? 9 : 4);
            }
        }
    }

    private String truncate(String text, int maxChars) {
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + "...";
    }
}
