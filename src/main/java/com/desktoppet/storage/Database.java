package com.desktoppet.storage;

import com.desktoppet.config.AppConfig;
import com.desktoppet.storage.entity.LongTermMemoryRecord;
import com.desktoppet.storage.mapper.ConversationMapper;
import com.desktoppet.storage.mapper.LongTermMemoryMapper;
import com.desktoppet.storage.mapper.ResourcePackMapper;
import com.desktoppet.storage.mapper.ScheduleMapper;
import com.desktoppet.storage.mapper.SessionSummaryMapper;
import com.desktoppet.storage.mapper.UserProfileMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;
import java.util.List;

public final class Database {
    private final HikariDataSource dataSource;
    private final SqlSessionFactory sqlSessionFactory;

    private Database(HikariDataSource dataSource) {
        this.dataSource = dataSource;
        this.sqlSessionFactory = createSqlSessionFactory(dataSource);
    }

    public static Database connect(AppConfig config) {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.mysqlJdbcUrl());
        hikari.setUsername(config.mysqlUsername());
        hikari.setPassword(config.mysqlPassword());
        hikari.setMaximumPoolSize(5);
        hikari.setPoolName("desktop-pet-mysql");
        return new Database(new HikariDataSource(hikari));
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public SqlSession openSession() {
        return sqlSessionFactory.openSession(true);
    }

    public void migrateIfConfigured() {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load()
                .migrate();
    }

    public void close() {
        dataSource.close();
    }

    public void saveConversation(String sessionId, String role, String content) {
        try (SqlSession session = openSession()) {
            session.getMapper(ConversationMapper.class).insert(sessionId, role, content);
        }
    }

    public List<String> loadImportantMemories() {
        try (SqlSession session = openSession()) {
            LongTermMemoryMapper mapper = session.getMapper(LongTermMemoryMapper.class);
            List<LongTermMemoryRecord> memories = mapper.selectWeighted(12);
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

    public void saveLongTermMemory(String memoryType, String content, int priority) {
        try (SqlSession session = openSession()) {
            session.getMapper(LongTermMemoryMapper.class).insert(memoryType, content, priority);
        }
    }

    private SqlSessionFactory createSqlSessionFactory(DataSource dataSource) {
        Environment environment = new Environment("default", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(ConversationMapper.class);
        configuration.addMapper(LongTermMemoryMapper.class);
        configuration.addMapper(ResourcePackMapper.class);
        configuration.addMapper(ScheduleMapper.class);
        configuration.addMapper(SessionSummaryMapper.class);
        configuration.addMapper(UserProfileMapper.class);
        return new SqlSessionFactoryBuilder().build(configuration);
    }
}
