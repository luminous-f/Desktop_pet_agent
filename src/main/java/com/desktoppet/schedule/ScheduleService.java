package com.desktoppet.schedule;

import com.desktoppet.storage.Database;
import com.desktoppet.storage.entity.ScheduleItemRecord;
import com.desktoppet.storage.mapper.ScheduleMapper;
import org.apache.ibatis.session.SqlSession;

import java.time.LocalDate;
import java.util.List;

public final class ScheduleService {
    private final Database database;

    public ScheduleService(Database database) {
        this.database = database;
    }

    public List<ScheduleItem> todayItems() {
        try (SqlSession session = database.openSession()) {
            return session.getMapper(ScheduleMapper.class).todayItems(LocalDate.now()).stream()
                    .map(this::toItem)
                    .toList();
        }
    }

    public void addTodayItem(String title) {
        try (SqlSession session = database.openSession()) {
            session.getMapper(ScheduleMapper.class).insertTodayItem(LocalDate.now(), title);
        }
    }

    private ScheduleItem toItem(ScheduleItemRecord record) {
        return new ScheduleItem(record.getId(), record.getItemDate(), record.getTitle(), record.isDone());
    }
}
