package com.desktoppet.storage.mapper;

import com.desktoppet.storage.entity.ScheduleItemRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

public interface ScheduleMapper {
    @Select("""
            SELECT id, item_date, title, done
            FROM schedule_items
            WHERE item_date = #{date}
            ORDER BY created_at
            """)
    List<ScheduleItemRecord> todayItems(@Param("date") LocalDate date);

    @Insert("INSERT INTO schedule_items(item_date, title) VALUES (#{date}, #{title})")
    void insertTodayItem(@Param("date") LocalDate date, @Param("title") String title);
}
