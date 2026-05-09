package com.desktoppet.service;

import dev.langchain4j.model.openai.OpenAiChatModel;

import java.util.List;

public interface ConversationMemoryService {
    void recordRaw(String sessionId, String role, String content);

    List<String> shortTermSnapshot();

    List<String> weightedLongTermMemories(int limit);

    List<String> recentSessionSummaries(int limit);

    void maybeRememberUserFact(String userMessage);

    boolean compressIfNeeded(String sessionId, OpenAiChatModel model, String reason, int currentPromptTokens);

    void summarizeOnShutdown(String sessionId, OpenAiChatModel model);

    int contextBudgetTokens();

    int estimateTokens(String text);
}
