package com.desktoppet.service;

import com.desktoppet.schedule.ScheduleItem;

import java.util.List;

public interface ScheduleService {
    List<ScheduleItem> todayItems();

    void addTodayItem(String title);
}
