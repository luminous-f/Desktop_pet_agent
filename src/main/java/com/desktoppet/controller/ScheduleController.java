package com.desktoppet.controller;

import com.desktoppet.common.AppException;
import com.desktoppet.controller.dto.ApiModels.ScheduleItemResponse;
import com.desktoppet.controller.dto.ApiModels.ScheduleRequest;
import com.desktoppet.schedule.ScheduleItem;
import com.desktoppet.service.ScheduleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/schedule/today")
public class ScheduleController {
    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    public List<ScheduleItemResponse> todaySchedule() {
        return scheduleResponses(scheduleService.todayItems());
    }

    @PostMapping
    public List<ScheduleItemResponse> addTodaySchedule(@RequestBody ScheduleRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            throw AppException.badRequest("title 不能为空");
        }
        scheduleService.addTodayItem(request.title().trim());
        return scheduleResponses(scheduleService.todayItems());
    }

    private List<ScheduleItemResponse> scheduleResponses(List<ScheduleItem> items) {
        return items.stream()
                .map(item -> new ScheduleItemResponse(item.id(), item.date().toString(), item.title(), item.done()))
                .toList();
    }
}
