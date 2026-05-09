package com.desktoppet.controller;

import com.desktoppet.controller.dto.ApiModels.NewsResponse;
import com.desktoppet.service.NewsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/news/daily")
public class NewsController {
    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @GetMapping
    public NewsResponse dailyNews() {
        return new NewsResponse(newsService.dailySummary());
    }
}
