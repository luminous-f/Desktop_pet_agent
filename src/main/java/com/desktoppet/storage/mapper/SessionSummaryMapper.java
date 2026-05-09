package com.desktoppet.storage.mapper;

import com.desktoppet.storage.entity.SessionSummaryRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SessionSummaryMapper {
    @Insert("""
            INSERT INTO session_summaries(session_id, summary_text, message_count, importance)
            VALUES (#{sessionId}, #{summaryText}, #{messageCount}, #{importance})
            """)
    void insert(@Param("sessionId") String sessionId,
                @Param("summaryText") String summaryText,
                @Param("messageCount") int messageCount,
                @Param("importance") int importance);

    @Select("""
            SELECT id, session_id, summary_text, message_count, importance, created_at
            FROM session_summaries
            ORDER BY (importance * 10 - TIMESTAMPDIFF(DAY, created_at, CURRENT_TIMESTAMP)) DESC, created_at DESC
            LIMIT #{limit}
            """)
    List<SessionSummaryRecord> selectRecentWeighted(@Param("limit") int limit);
}
