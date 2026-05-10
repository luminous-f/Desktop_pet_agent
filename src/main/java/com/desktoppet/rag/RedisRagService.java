package com.desktoppet.rag;

import com.desktoppet.config.AppConfig;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.search.FTCreateParams;
import redis.clients.jedis.search.IndexDataType;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.Document;
import redis.clients.jedis.search.SearchResult;
import redis.clients.jedis.search.schemafields.TagField;
import redis.clients.jedis.search.schemafields.TextField;
import redis.clients.jedis.search.schemafields.VectorField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Service
public class RedisRagService implements RagService {
    private static final Logger log = LoggerFactory.getLogger(RedisRagService.class);

    private static final String BASE_CHUNK_KEY_PREFIX = "desktop-pet:rag:chunk:";
    private static final String BASE_PARENT_KEY_PREFIX = "desktop-pet:rag:parent:";
    private static final Pattern CASTORICE_SPEECH_PATTERN = Pattern.compile(
            "(^|\\R)\\s*(?:「?遐蝶」?|灰黯之手，遐蝶|「?灰黯之手，遐蝶」?|记忆中的遐蝶)\\s*[：:]"
    );
    private static final List<String> CASTORICE_MENTION_ALIASES = List.of("遐蝶", "小蝶", "Castorice");
    private static final List<String> DEFAULT_DOCUMENTS = List.of(
            "/assets/rag/Castorice/README.md",
            "/assets/rag/Castorice/world.md",
            "/assets/rag/Castorice/story.docx"
    );

    private final JedisPooled jedis;
    private final String searchIndex;
    private final String chunkKeyPrefix;
    private final String parentKeyPrefix;
    private final AppConfig config;
    private final EmbeddingModel embeddingModel;

    public RedisRagService(AppConfig config) {
        this.config = config;
        this.jedis = createJedis(config);
        this.embeddingModel = createEmbeddingModel(config);
        String embeddingNamespace = sanitizeId(config.embeddingModel() + "_" + config.embeddingDimensions());
        this.searchIndex = config.redisSearchIndex() + "_" + embeddingNamespace;
        this.chunkKeyPrefix = BASE_CHUNK_KEY_PREFIX + embeddingNamespace + ":";
        this.parentKeyPrefix = BASE_PARENT_KEY_PREFIX + embeddingNamespace + ":";
        initializeIndex();
        indexPackagedDocuments();
    }

    private JedisPooled createJedis(AppConfig config) {
        DefaultJedisClientConfig.Builder clientConfig = DefaultJedisClientConfig.builder()
                .database(config.redisDatabase());
        if (!config.redisPassword().isBlank()) {
            clientConfig.password(config.redisPassword());
        }
        JedisClientConfig jedisConfig = clientConfig.build();
        JedisPooled pooled = new JedisPooled(new HostAndPort(config.redisHost(), config.redisPort()), jedisConfig);
        try {
            pooled.ping();
            return pooled;
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("without any password configured")) {
                System.err.println("Redis password was provided, but the local Redis server has no password; retrying without AUTH.");
                pooled.close();
                JedisClientConfig noAuthConfig = DefaultJedisClientConfig.builder()
                        .database(config.redisDatabase())
                        .build();
                return new JedisPooled(new HostAndPort(config.redisHost(), config.redisPort()), noAuthConfig);
            }
            throw e;
        }
    }

    @Override
    public List<String> retrieve(String query) {
        return debugRetrieve(query).stream()
                .map(hit -> hit.source() + " / " + hit.parentId() + ":\n" + hit.text())
                .toList();
    }

    @Override
    public List<RagDebugHit> debugRetrieve(String query) {
        if (embeddingModel == null || query == null || query.isBlank()) {
            return List.of();
        }
        try {
            byte[] queryVector = vectorBytes(embeddingModel.embed(query).content().vector());
            int resultLimit = Math.max(1, config.ragTopK());
            int candidateLimit = Math.max(resultLimit, Math.min(50, resultLimit * 5));
            Query redisQuery = new Query("*=>[KNN " + candidateLimit + " @embedding $vec AS vector_score]")
                    .addParam("vec", queryVector)
                    .returnFields("source", "parent_id", "text", "vector_score")
                    .limit(0, candidateLimit)
                    .dialect(2);
            SearchResult result = jedis.ftSearch(searchIndex, redisQuery);
            List<Document> relevantDocuments = result.getDocuments().stream()
                    .filter(document -> isRelevant(document.getString("vector_score")))
                    .sorted(Comparator.comparingDouble(this::weightedDistance))
                    .limit(resultLimit)
                    .toList();
            List<RagDebugHit> hits = new ArrayList<>();
            for (int i = 0; i < relevantDocuments.size(); i++) {
                Document document = relevantDocuments.get(i);
                String text = document.getString("text");
                double boost = retrievalBoost(text);
                double weightedScore = weightedDistance(document);
                log.info(
                        "RAG hit #{}: source={}, parent={}, score={}, dialogueBoost={}, text={}",
                        i + 1,
                        document.getString("source"),
                        document.getString("parent_id"),
                        document.getString("vector_score"),
                        boost,
                        summarize(text, 160)
                );
                hits.add(new RagDebugHit(
                        i + 1,
                        document.getString("source"),
                        document.getString("parent_id"),
                        document.getString("vector_score"),
                        boost,
                        weightedScore,
                        retrievalBoostReason(text),
                        text == null ? 0 : text.length(),
                        text
                ));
            }
            log.info("RAG retrieve completed: queryLength={}, hits={}", query.length(), hits.size());
            return List.copyOf(hits);
        } catch (Exception e) {
            log.warn("RAG retrieve failed: {}", e.getMessage());
            return List.of();
        }
    }

    public List<RagDebugHit> debugRetrieveRawCandidates(String query) {
        if (embeddingModel == null || query == null || query.isBlank()) {
            return List.of();
        }
        try {
            byte[] queryVector = vectorBytes(embeddingModel.embed(query).content().vector());
            int resultLimit = Math.max(1, config.ragTopK());
            int candidateLimit = Math.max(resultLimit, Math.min(50, resultLimit * 5));
            Query redisQuery = new Query("*=>[KNN " + candidateLimit + " @embedding $vec AS vector_score]")
                    .addParam("vec", queryVector)
                    .returnFields("source", "parent_id", "text", "vector_score")
                    .limit(0, candidateLimit)
                    .dialect(2);
            SearchResult result = jedis.ftSearch(searchIndex, redisQuery);
            List<Document> documents = result.getDocuments().stream()
                    .filter(document -> isRelevant(document.getString("vector_score")))
                    .toList();
            List<RagDebugHit> hits = new ArrayList<>();
            for (int i = 0; i < documents.size(); i++) {
                Document document = documents.get(i);
                String text = document.getString("text");
                double boost = retrievalBoost(text);
                hits.add(new RagDebugHit(
                        i + 1,
                        document.getString("source"),
                        document.getString("parent_id"),
                        document.getString("vector_score"),
                        boost,
                        weightedDistance(document),
                        retrievalBoostReason(text),
                        text == null ? 0 : text.length(),
                        text
                ));
            }
            return List.copyOf(hits);
        } catch (Exception e) {
            log.warn("RAG raw candidate debug retrieve failed: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public void upsertDocument(String id, String text) {
        indexDocumentIfNeeded(id, text);
    }

    private boolean indexDocumentIfNeeded(String id, String text) {
        if (embeddingModel == null || text == null || text.isBlank()) {
            return false;
        }
        String parentId = sanitizeId(id);
        List<String> chunks = chunkText(text);
        String parentKey = parentKeyPrefix + parentId;
        String contentHash = contentHash(text);
        String existingHash = jedis.hget(parentKey, "content_hash");
        String existingChunkCount = jedis.hget(parentKey, "chunk_count");
        if (contentHash.equals(existingHash) && chunksPresent(parentId, existingChunkCount)) {
            log.info("RAG document unchanged and complete; skipped: source={}, chunks={}", id, existingChunkCount);
            return false;
        }
        deleteExistingChunks(parentId, existingChunkCount);
        jedis.hset(parentKey, Map.of(
                "id", parentId,
                "source", id,
                "text", text,
                "content_hash", contentHash,
                "chunk_count", Integer.toString(chunks.size())
        ));
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            Embedding embedding = embeddingModel.embed(chunk).content();
            Map<byte[], byte[]> hash = new HashMap<>();
            hash.put(bytes("parent_id"), bytes(parentId));
            hash.put(bytes("source"), bytes(id));
            hash.put(bytes("text"), bytes(chunk));
            hash.put(bytes("embedding"), vectorBytes(embedding.vector()));
            jedis.hset(bytes(chunkKeyPrefix + parentId + ":" + i), hash);
        }
        log.info("RAG document indexed: source={}, chunks={}", id, chunks.size());
        return true;
    }

    private EmbeddingModel createEmbeddingModel(AppConfig config) {
        if (config.embeddingApiKey().isBlank() || config.embeddingModel().isBlank()) {
            return null;
        }
        if ("qwen3-vl-embedding".equals(config.embeddingModel())) {
            return new DashScopeVlEmbeddingModel(
                    config.embeddingBaseUrl(),
                    config.embeddingApiKey(),
                    config.embeddingModel(),
                    config.embeddingDimensions(),
                    Duration.ofSeconds(45)
            );
        }
        return OpenAiEmbeddingModel.builder()
                .baseUrl(config.embeddingBaseUrl())
                .apiKey(config.embeddingApiKey())
                .modelName(config.embeddingModel())
                .dimensions(config.embeddingDimensions())
                .timeout(Duration.ofSeconds(45))
                .build();
    }

    private void initializeIndex() {
        if (embeddingModel == null) {
            System.err.println("RAG disabled: embedding API key or model is not configured.");
            return;
        }
        try {
            jedis.ftInfo(searchIndex);
            return;
        } catch (Exception ignored) {
        }
        try {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("TYPE", "FLOAT32");
            attributes.put("DIM", config.embeddingDimensions());
            attributes.put("DISTANCE_METRIC", "COSINE");
            attributes.put("INITIAL_CAP", 1024);
            attributes.put("M", 16);
            attributes.put("EF_CONSTRUCTION", 200);
            jedis.ftCreate(
                    searchIndex,
                    FTCreateParams.createParams().on(IndexDataType.HASH).prefix(chunkKeyPrefix),
                    TextField.of("text"),
                    TagField.of("parent_id"),
                    TextField.of("source"),
                    VectorField.builder()
                            .fieldName("embedding")
                            .algorithm(VectorField.VectorAlgorithm.HNSW)
                            .attributes(attributes)
                            .build()
            );
        } catch (Exception e) {
            System.err.println("RAG index initialization failed for " + searchIndex + ": " + e.getMessage());
        }
    }

    @Override
    public RagIndexingResult reindexPackagedDocuments() {
        List<String> messages = new ArrayList<>();
        if (embeddingModel == null) {
            return new RagIndexingResult(0, 0, List.of("RAG disabled: embedding API key or model is not configured."));
        }
        int indexed = 0;
        int skipped = 0;
        try {
            for (String resource : DEFAULT_DOCUMENTS) {
                String text = readResource(resource);
                if (text.isBlank()) {
                    messages.add(resource + " is empty or unreadable; skipped.");
                    skipped++;
                    continue;
                }
                if (indexDocumentIfNeeded(resource, text)) {
                    messages.add(resource + " indexed.");
                    indexed++;
                } else {
                    messages.add(resource + " unchanged and complete; skipped.");
                    skipped++;
                }
            }
        } catch (Exception e) {
            messages.add("RAG packaged document indexing failed: " + e.getMessage());
            log.warn(messages.get(messages.size() - 1));
        }
        log.info("RAG packaged indexing finished: indexed={}, skipped={}", indexed, skipped);
        return new RagIndexingResult(indexed, skipped, List.copyOf(messages));
    }

    private void indexPackagedDocuments() {
        reindexPackagedDocuments();
    }

    private List<String> chunkText(String text) {
        int chunkChars = Math.max(300, config.ragChunkChars());
        int overlap = Math.max(0, Math.min(config.ragChunkOverlapChars(), chunkChars / 2));
        List<String> chunks = new ArrayList<>();
        String[] paragraphs = text.replace("\r\n", "\n").split("\\n\\s*\\n|\\n");
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            String normalized = paragraph.trim();
            if (normalized.isBlank()) {
                continue;
            }
            if (normalized.length() > chunkChars) {
                flushChunk(chunks, current);
                addSlidingChunks(chunks, normalized, chunkChars, overlap);
                continue;
            }
            if (!current.isEmpty() && current.length() + normalized.length() + 1 > chunkChars) {
                String previous = current.toString();
                flushChunk(chunks, current);
                String tail = tail(previous, overlap);
                if (!tail.isBlank()) {
                    current.append(tail);
                }
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(normalized);
        }
        flushChunk(chunks, current);
        return chunks;
    }

    private String readResource(String resource) {
        if (resource.endsWith(".docx")) {
            return readDocxResource(resource);
        }
        try (InputStream in = RedisRagService.class.getResourceAsStream(resource)) {
            if (in == null) {
                return "";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private String readDocxResource(String resource) {
        try (InputStream in = RedisRagService.class.getResourceAsStream(resource)) {
            if (in == null) {
                return "";
            }
            try (XWPFDocument document = new XWPFDocument(in)) {
                return document.getParagraphs().stream()
                        .map(XWPFParagraph::getText)
                        .map(String::trim)
                        .filter(text -> !text.isBlank())
                        .collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            return "";
        }
    }

    private boolean isRelevant(String vectorScore) {
        if (vectorScore == null || vectorScore.isBlank()) {
            return false;
        }
        try {
            return Double.parseDouble(vectorScore) <= config.ragMaxVectorDistance();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private double weightedDistance(Document document) {
        double distance = parseDouble(document.getString("vector_score"), Double.MAX_VALUE);
        return distance - retrievalBoost(document.getString("text"));
    }

    private double retrievalBoost(String text) {
        if (text == null || text.isBlank()) {
            return 0.0;
        }
        if (CASTORICE_SPEECH_PATTERN.matcher(text).find()) {
            return 0.12;
        }
        if (CASTORICE_MENTION_ALIASES.stream().anyMatch(text::contains)) {
            return 0.06;
        }
        return 0.0;
    }

    private String retrievalBoostReason(String text) {
        if (text == null || text.isBlank()) {
            return "none";
        }
        if (CASTORICE_SPEECH_PATTERN.matcher(text).find()) {
            return "castorice_speech";
        }
        if (CASTORICE_MENTION_ALIASES.stream().anyMatch(text::contains)) {
            return "castorice_mention";
        }
        return "none";
    }

    private String summarize(String text, int maxChars) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, maxChars) + "...";
    }

    private void deleteExistingChunks(String parentId, String chunkCount) {
        int count = parseInt(chunkCount, -1);
        if (count >= 0) {
            for (int i = 0; i < count; i++) {
                jedis.del(chunkKeyPrefix + parentId + ":" + i);
            }
            return;
        }
        Set<String> keys = jedis.keys(chunkKeyPrefix + parentId + ":*");
        if (!keys.isEmpty()) {
            jedis.del(keys.toArray(String[]::new));
        }
    }

    private boolean chunksPresent(String parentId, String chunkCount) {
        int count = parseInt(chunkCount, -1);
        if (count <= 0) {
            return false;
        }
        for (int i = 0; i < count; i++) {
            if (!jedis.exists(chunkKeyPrefix + parentId + ":" + i)) {
                return false;
            }
        }
        return true;
    }

    private void flushChunk(List<String> chunks, StringBuilder current) {
        String chunk = current.toString().trim();
        if (!chunk.isBlank()) {
            chunks.add(chunk);
        }
        current.setLength(0);
    }

    private void addSlidingChunks(List<String> chunks, String text, int chunkChars, int overlap) {
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + chunkChars);
            String chunk = text.substring(start, end).trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            if (end == text.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
    }

    private String tail(String text, int maxChars) {
        if (maxChars <= 0 || text.isBlank()) {
            return "";
        }
        int start = Math.max(0, text.length() - maxChars);
        return text.substring(start).trim();
    }

    private int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private double parseDouble(String value, double fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String contentHash(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private byte[] vectorBytes(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : vector) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private String sanitizeId(String id) {
        return id.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
