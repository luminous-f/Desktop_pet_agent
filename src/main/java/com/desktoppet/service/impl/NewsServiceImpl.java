package com.desktoppet.service.impl;

import com.desktoppet.service.NewsService;
import com.desktoppet.service.SearchService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class NewsServiceImpl implements NewsService {
    private final SearchService searchService;

    public NewsServiceImpl(SearchService searchService) {
        this.searchService = searchService;
    }

    @Override
    public String dailySummary() {
        return searchService.search(LocalDate.now() + " 今日重要新闻 摘要");
    }
}
