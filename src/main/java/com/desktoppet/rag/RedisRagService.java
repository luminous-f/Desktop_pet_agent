package com.desktoppet.rag;

import com.desktoppet.config.AppConfig;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.search.FTCreateParams;
import redis.clients.jedis.search.IndexDataType;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.SearchResult;
import redis.clients.jedis.search.schemafields.TagField;
import redis.clients.jedis.search.schemafields.TextField;
import redis.clients.jedis.search.schemafields.VectorField;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RedisRagService implements RagService {
    private static final String BASE_CHUNK_KEY_PREFIX = "desktop-pet:rag:chunk:";
    private static final String BASE_PARENT_KEY_PREFIX = "desktop-pet:rag:parent:";
    private static final List<String> DEFAULT_DOCUMENTS = List.of(
            "/assets/rag/Castorice/README.md",
            "/assets/rag/Castorice/world.md"
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
        if (embeddingModel == null || query == null || query.isBlank()) {
            return List.of();
        }
        try {
            byte[] queryVector = vectorBytes(embeddingModel.embed(query).content().vector());
            Query redisQuery = new Query("*=>[KNN " + config.ragTopK() + " @embedding $vec AS vector_score]")
                    .addParam("vec", queryVector)
                    .returnFields("source", "parent_id", "text", "vector_score")
                    .limit(0, config.ragTopK())
                    .dialect(2);
            SearchResult result = jedis.ftSearch(searchIndex, redisQuery);
            return result.getDocuments().stream()
                    .map(document -> document.getString("source") + " / " + document.getString("parent_id")
                            + ":\n" + document.getString("text"))
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public void upsertDocument(String id, String text) {
        if (embeddingModel == null || text == null || text.isBlank()) {
            return;
        }
        String parentId = sanitizeId(id);
        jedis.hset(parentKeyPrefix + parentId, Map.of("id", parentId, "text", text));
        List<String> chunks = chunkText(text);
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

    private void indexPackagedDocuments() {
        if (embeddingModel == null) {
            return;
        }
        try {
            for (String resource : DEFAULT_DOCUMENTS) {
                String text = readResource(resource);
                if (!text.isBlank() && !jedis.exists(parentKeyPrefix + sanitizeId(resource))) {
                    upsertDocument(resource, text);
                }
            }
        } catch (Exception e) {
            System.err.println("RAG packaged document indexing failed: " + e.getMessage());
        }
    }

    private List<String> chunkText(String text) {
        int chunkChars = Math.max(300, config.ragChunkChars());
        int overlap = Math.max(0, Math.min(config.ragChunkOverlapChars(), chunkChars / 2));
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + chunkChars);
            chunks.add(text.substring(start, end).trim());
            if (end == text.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        return chunks;
    }

    private String readResource(String resource) {
        try (InputStream in = RedisRagService.class.getResourceAsStream(resource)) {
            if (in == null) {
                return "";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
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
