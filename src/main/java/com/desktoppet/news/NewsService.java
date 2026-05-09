package com.desktoppet.news;

import java.time.LocalDate;

public final class NewsService {
    private final SearchService searchService;

    public NewsService(SearchService searchService) {
        this.searchService = searchService;
    }

    public String dailySummary() {
        return searchService.search(LocalDate.now() + " 今日重要新闻 摘要");
    }
}
