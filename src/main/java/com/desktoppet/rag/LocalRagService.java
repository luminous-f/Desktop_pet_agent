package com.desktoppet.rag;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class LocalRagService implements RagService {
    private static final Pattern CASTORICE_SPEECH_PATTERN = Pattern.compile(
            "(^|\\R)\\s*(?:「?遐蝶」?|灰黯之手，遐蝶|「?灰黯之手，遐蝶」?|记忆中的遐蝶)\\s*[：:]"
    );

    private static final List<String> DEFAULT_DOCUMENTS = List.of(
            "/assets/rag/Castorice/README.md",
            "/assets/rag/Castorice/world.md"
    );

    private final List<DocumentChunk> chunks;

    public LocalRagService() {
        this.chunks = loadChunks();
    }

    @Override
    public List<String> retrieve(String query) {
        if (query == null || query.isBlank() || chunks.isEmpty()) {
            return List.of();
        }
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        return chunks.stream()
                .map(chunk -> new ScoredChunk(chunk, score(chunk.text().toLowerCase(Locale.ROOT), normalizedQuery)))
                .filter(scored -> scored.score() > 0)
                .sorted(Comparator.comparingInt(ScoredChunk::score).reversed())
                .limit(3)
                .map(scored -> scored.chunk().source() + ":\n" + scored.chunk().text())
                .toList();
    }

    @Override
    public void upsertDocument(String id, String text) {
        throw new UnsupportedOperationException("Local RAG reads packaged resources only.");
    }

    @Override
    public RagIndexingResult reindexPackagedDocuments() {
        return new RagIndexingResult(0, chunks.size(), List.of("Local RAG reads packaged resources only."));
    }

    private List<DocumentChunk> loadChunks() {
        List<DocumentChunk> loaded = new ArrayList<>();
        for (String resource : DEFAULT_DOCUMENTS) {
            String text = readResource(resource);
            if (text.isBlank()) {
                continue;
            }
            for (String chunk : text.split("\\n\\s*\\n")) {
                String normalized = chunk.trim();
                if (!normalized.isBlank()) {
                    loaded.add(new DocumentChunk(resource, normalized));
                }
            }
        }
        return loaded;
    }

    private String readResource(String resource) {
        try (InputStream in = LocalRagService.class.getResourceAsStream(resource)) {
            if (in == null) {
                return "";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private int score(String text, String query) {
        int score = 0;
        if (CASTORICE_SPEECH_PATTERN.matcher(text).find()) {
            score += 6;
        } else if (text.contains("遐蝶") || text.contains("小蝶") || text.contains("castorice")) {
            score += 3;
        }
        for (String token : query.split("\\s+")) {
            if (!token.isBlank() && text.contains(token)) {
                score++;
            }
        }
        for (String keyword : List.of("遐蝶", "小蝶", "Castorice", "翁法洛斯", "死亡", "阁下", "开拓者", "黄金裔")) {
            String normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
            if (query.contains(normalizedKeyword) && text.contains(normalizedKeyword)) {
                score += 3;
            }
        }
        return score;
    }

    private record DocumentChunk(String source, String text) {
    }

    private record ScoredChunk(DocumentChunk chunk, int score) {
    }
}
