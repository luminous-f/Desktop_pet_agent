package com.desktoppet.service.impl;

import com.desktoppet.agent.DesktopPetAgent;
import com.desktoppet.controller.dto.ApiModels.ScheduleItemResponse;
import com.desktoppet.controller.dto.ApiModels.StartupResponse;
import com.desktoppet.schedule.ScheduleItem;
import com.desktoppet.service.ProfileService;
import com.desktoppet.service.ScheduleService;
import com.desktoppet.service.StartupService;
import com.desktoppet.service.WeatherService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StartupServiceImpl implements StartupService {
    private final DesktopPetAgent agent;
    private final WeatherService weatherService;
    private final ProfileService profileService;
    private final ScheduleService scheduleService;

    public StartupServiceImpl(
            DesktopPetAgent agent,
            WeatherService weatherService,
            ProfileService profileService,
            ScheduleService scheduleService
    ) {
        this.agent = agent;
        this.weatherService = weatherService;
        this.profileService = profileService;
        this.scheduleService = scheduleService;
    }

    @Override
    public StartupResponse startup() {
        return new StartupResponse(
                agent.startupNotice(),
                weatherService.todayWeather(),
                profileService.currentProfile(),
                scheduleResponses(scheduleService.todayItems())
        );
    }

    private List<ScheduleItemResponse> scheduleResponses(List<ScheduleItem> items) {
        return items.stream()
                .map(item -> new ScheduleItemResponse(item.id(), item.date().toString(), item.title(), item.done()))
                .toList();
    }
}
