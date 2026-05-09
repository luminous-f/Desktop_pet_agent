package com.desktoppet.infrastructure.config;

import com.desktoppet.config.AppConfig;
import com.desktoppet.service.SearchService;
import com.desktoppet.service.impl.OpenWebSearchServiceImpl;
import com.desktoppet.service.impl.TavilySearchServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SearchConfiguration {
    @Bean
    public SearchService searchService(AppConfig config) {
        if ("tavily".equalsIgnoreCase(config.searchProvider())) {
            return new TavilySearchServiceImpl(config);
        }
        return new OpenWebSearchServiceImpl(config);
    }
}
