package com.desktoppet.service.impl;

import com.desktoppet.entity.ScheduleItemRecord;
import com.desktoppet.dao.ScheduleMapper;
import com.desktoppet.schedule.ScheduleItem;
import com.desktoppet.service.ScheduleService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class ScheduleServiceImpl implements ScheduleService {
    private final ScheduleMapper scheduleMapper;

    public ScheduleServiceImpl(ScheduleMapper scheduleMapper) {
        this.scheduleMapper = scheduleMapper;
    }

    @Override
    public List<ScheduleItem> todayItems() {
        return scheduleMapper.todayItems(LocalDate.now()).stream()
                .map(this::toItem)
                .toList();
    }

    @Override
    public void addTodayItem(String title) {
        scheduleMapper.insertTodayItem(LocalDate.now(), title);
    }

    private ScheduleItem toItem(ScheduleItemRecord record) {
        return new ScheduleItem(record.getId(), record.getItemDate(), record.getTitle(), record.isDone());
    }
}
