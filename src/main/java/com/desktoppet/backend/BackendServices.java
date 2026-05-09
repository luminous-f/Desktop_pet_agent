package com.desktoppet.backend;

import com.desktoppet.agent.DesktopPetAgent;
import com.desktoppet.agent.LangChainDesktopPetAgent;
import com.desktoppet.config.AppConfig;
import com.desktoppet.files.FileOrganizer;
import com.desktoppet.memory.ConversationMemoryService;
import com.desktoppet.memory.InMemoryConversationMemory;
import com.desktoppet.news.NewsService;
import com.desktoppet.news.OpenWebSearchService;
import com.desktoppet.news.SearchService;
import com.desktoppet.news.TavilySearchService;
import com.desktoppet.profile.UserProfileService;
import com.desktoppet.rag.RedisRagService;
import com.desktoppet.resources.CharacterContextService;
import com.desktoppet.schedule.ScheduleService;
import com.desktoppet.storage.Database;
import com.desktoppet.weather.QWeatherService;

public final class BackendServices implements AutoCloseable {
    private final AppConfig config;
    private final Database database;
    private final DesktopPetAgent agent;
    private final QWeatherService weatherService;
    private final ScheduleService scheduleService;
    private final NewsService newsService;
    private final FileOrganizer fileOrganizer;
    private final UserProfileService profileService;

    private BackendServices(
            AppConfig config,
            Database database,
            DesktopPetAgent agent,
            QWeatherService weatherService,
            ScheduleService scheduleService,
            NewsService newsService,
            FileOrganizer fileOrganizer,
            UserProfileService profileService
    ) {
        this.config = config;
        this.database = database;
        this.agent = agent;
        this.weatherService = weatherService;
        this.scheduleService = scheduleService;
        this.newsService = newsService;
        this.fileOrganizer = fileOrganizer;
        this.profileService = profileService;
    }

    public static BackendServices create() {
        AppConfig config = AppConfig.load();
        Database database = Database.connect(config);
        database.migrateIfConfigured();

        SearchService searchService = createSearchService(config);
        QWeatherService weatherService = new QWeatherService(config);
        ScheduleService scheduleService = new ScheduleService(database);
        UserProfileService profileService = new UserProfileService(database);
        NewsService newsService = new NewsService(searchService);
        FileOrganizer fileOrganizer = new FileOrganizer(config);
        RedisRagService ragService = new RedisRagService(config);
        String characterContext = new CharacterContextService().loadDefaultContext();
        ConversationMemoryService memoryService = new ConversationMemoryService(
                database,
                new InMemoryConversationMemory(32),
                config
        );
        DesktopPetAgent agent = new LangChainDesktopPetAgent(
                config,
                memoryService,
                weatherService,
                searchService,
                scheduleService,
                newsService,
                profileService,
                fileOrganizer,
                ragService,
                characterContext
        );
        return new BackendServices(
                config,
                database,
                agent,
                weatherService,
                scheduleService,
                newsService,
                fileOrganizer,
                profileService
        );
    }

    public AppConfig config() {
        return config;
    }

    public DesktopPetAgent agent() {
        return agent;
    }

    public QWeatherService weatherService() {
        return weatherService;
    }

    public ScheduleService scheduleService() {
        return scheduleService;
    }

    public NewsService newsService() {
        return newsService;
    }

    public FileOrganizer fileOrganizer() {
        return fileOrganizer;
    }

    public UserProfileService profileService() {
        return profileService;
    }

    @Override
    public void close() {
        agent.shutdown();
        database.close();
    }

    private static SearchService createSearchService(AppConfig config) {
        if ("tavily".equalsIgnoreCase(config.searchProvider())) {
            return new TavilySearchService(config);
        }
        return new OpenWebSearchService(config);
    }
}
