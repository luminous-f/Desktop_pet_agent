package com.desktoppet.infrastructure.config;

import com.desktoppet.config.AppConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfigConfiguration {
    @Bean
    public AppConfig appConfig() {
        return AppConfig.load();
    }
}
