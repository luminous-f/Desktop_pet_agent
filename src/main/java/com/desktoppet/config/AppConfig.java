package com.desktoppet.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public final class AppConfig {
    private final Properties properties;
    private volatile List<Path> allowedRootsOverride;

    private AppConfig(Properties properties) {
        this.properties = properties;
    }

    public static AppConfig load() {
        Properties properties = new Properties();
        try (InputStream in = AppConfig.class.getResourceAsStream("/application.properties")) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException ignored) {
        }
        Path external = Path.of("config", "application.properties");
        if (Files.isRegularFile(external)) {
            try (InputStream in = Files.newInputStream(external)) {
                properties.load(in);
            } catch (IOException ignored) {
            }
        }
        return new AppConfig(properties);
    }

    public String deepSeekApiKey() {
        return value("DEEPSEEK_API_KEY", "deepseek.apiKey", "");
    }

    public String deepSeekBaseUrl() {
        return value("DEEPSEEK_BASE_URL", "deepseek.baseUrl", "https://api.deepseek.com/v1");
    }

    public String deepSeekChatModel() {
        return value("DEEPSEEK_CHAT_MODEL", "deepseek.chatModel", "deepseek-chat");
    }

    public String embeddingApiKey() {
        return value("EMBEDDING_API_KEY", "embedding.apiKey", deepSeekApiKey());
    }

    public String embeddingBaseUrl() {
        return value("EMBEDDING_BASE_URL", "embedding.baseUrl", deepSeekBaseUrl());
    }

    public String embeddingModel() {
        return value("EMBEDDING_MODEL", "embedding.model", "");
    }

    public int embeddingDimensions() {
        return Integer.parseInt(value("EMBEDDING_DIMENSIONS", "embedding.dimensions", "1024"));
    }

    public int contextBudgetTokens() {
        return Integer.parseInt(value("CONTEXT_BUDGET_TOKENS", "context.budgetTokens", "16000"));
    }

    public int ragChunkChars() {
        return Integer.parseInt(value("RAG_CHUNK_CHARS", "rag.chunkChars", "900"));
    }

    public int ragChunkOverlapChars() {
        return Integer.parseInt(value("RAG_CHUNK_OVERLAP_CHARS", "rag.chunkOverlapChars", "120"));
    }

    public int ragTopK() {
        return Integer.parseInt(value("RAG_TOP_K", "rag.topK", "5"));
    }

    public double ragMaxVectorDistance() {
        return Double.parseDouble(value("RAG_MAX_VECTOR_DISTANCE", "rag.maxVectorDistance", "0.70"));
    }

    public String mysqlJdbcUrl() {
        String host = value("MYSQL_HOST", "mysql.host", "127.0.0.1");
        String port = value("MYSQL_PORT", "mysql.port", "3306");
        String database = value("MYSQL_DATABASE", "mysql.database", "desktop_pet_agent");
        return "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    }

    public String mysqlUsername() {
        return value("MYSQL_USERNAME", "mysql.username", "root");
    }

    public String mysqlPassword() {
        return value("MYSQL_PASSWORD", "mysql.password", "");
    }

    public String qWeatherApiKey() {
        return value(List.of("WEATHER_API_KEY", "QWEATHER_API_KEY"), "qweather.apiKey", "");
    }

    public String redisHost() {
        return value("REDIS_HOST", "redis.host", "127.0.0.1");
    }

    public int redisPort() {
        return Integer.parseInt(value("REDIS_PORT", "redis.port", "6379"));
    }

    public String redisPassword() {
        return value("REDIS_PASSWORD", "redis.password", "");
    }

    public int redisDatabase() {
        return Integer.parseInt(value("REDIS_DATABASE", "redis.database", "0"));
    }

    public String redisSearchIndex() {
        return value("REDIS_SEARCH_INDEX", "redis.searchIndex", "desktop_pet_rag");
    }

    public String qWeatherBaseUrl() {
        return value("QWEATHER_BASE_URL", "qweather.baseUrl", "https://devapi.qweather.com");
    }

    public String qWeatherLocation() {
        return value("QWEATHER_LOCATION", "qweather.location", "101010100");
    }

    public String searchApiKey() {
        return value("SEARCH_API_KEY", "search.apiKey", "");
    }

    public String searchProvider() {
        return value("SEARCH_PROVIDER", "search.provider", "open-websearch");
    }

    public String searchBaseUrl() {
        return value("SEARCH_BASE_URL", "search.baseUrl", "http://127.0.0.1:3000/search");
    }

    public Path exportDir() {
        return Path.of(value("PET_EXPORT_DIR", "pet.exportDir", "organized"));
    }

    public List<Path> allowedRoots() {
        if (allowedRootsOverride != null) {
            return allowedRootsOverride;
        }
        String raw = value("PET_ALLOWED_ROOTS", "pet.allowedRoots", "");
        return parsePathList(raw);
    }

    public synchronized void replaceAllowedRoots(List<Path> roots) {
        allowedRootsOverride = List.copyOf(roots);
        properties.setProperty("pet.allowedRoots", allowedRootsPropertyValue());
    }

    public String allowedRootsPropertyValue() {
        return allowedRoots().stream()
                .map(Path::toString)
                .reduce((left, right) -> left + ";" + right)
                .orElse("");
    }

    private List<Path> parsePathList(String raw) {
        if (raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Path::of)
                .toList();
    }

    public String serverHost() {
        return value("SERVER_HOST", "server.host", "127.0.0.1");
    }

    public int serverPort() {
        return Integer.parseInt(value("SERVER_PORT", "server.port", "8080"));
    }

    public String backendBaseUrl() {
        return value("BACKEND_BASE_URL", "backend.baseUrl", "http://127.0.0.1:8080");
    }

    private String value(String envName, String propertyName, String fallback) {
        return value(List.of(envName), propertyName, fallback);
    }

    private String value(List<String> envNames, String propertyName, String fallback) {
        for (String envName : envNames) {
            String env = System.getenv(envName);
            if (env != null && !env.isBlank()) {
                return env;
            }
        }
        String property = properties.getProperty(propertyName, fallback);
        return resolvePlaceholder(property, fallback);
    }

    private String resolvePlaceholder(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        if (!value.startsWith("${") || !value.endsWith("}")) {
            return value;
        }
        String body = value.substring(2, value.length() - 1);
        String[] parts = body.split(":", 2);
        String envValue = System.getenv(parts[0]);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return parts.length == 2 ? parts[1] : fallback;
    }
}
