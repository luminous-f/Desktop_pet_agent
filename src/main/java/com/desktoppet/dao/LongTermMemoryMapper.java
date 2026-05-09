package com.desktoppet.dao;

import com.desktoppet.entity.LongTermMemoryRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface LongTermMemoryMapper {
    @Insert("INSERT INTO long_term_memories(memory_type, content, priority) VALUES (#{memoryType}, #{content}, #{priority})")
    void insert(@Param("memoryType") String memoryType, @Param("content") String content, @Param("priority") int priority);

    @Select("""
            SELECT id, memory_type, content, priority, access_count, last_accessed_at, created_at, updated_at,
                   (priority * 10 + access_count * 2 - TIMESTAMPDIFF(DAY, updated_at, CURRENT_TIMESTAMP)) AS memory_score
            FROM long_term_memories
            ORDER BY memory_score DESC, updated_at DESC
            LIMIT #{limit}
            """)
    List<LongTermMemoryRecord> selectWeighted(@Param("limit") int limit);

    @Update("""
            UPDATE long_term_memories
            SET access_count = access_count + 1, last_accessed_at = CURRENT_TIMESTAMP
            WHERE id IN (${idsCsv})
            """)
    void markAccessed(@Param("idsCsv") String idsCsv);
}
