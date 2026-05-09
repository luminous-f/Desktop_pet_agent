package com.desktoppet.dao;

import com.desktoppet.entity.ConversationRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ConversationMapper {
    @Insert("INSERT INTO conversations(session_id, role, content) VALUES (#{sessionId}, #{role}, #{content})")
    void insert(@Param("sessionId") String sessionId, @Param("role") String role, @Param("content") String content);

    @Select("""
            SELECT id, session_id, role, content, created_at
            FROM conversations
            WHERE session_id = #{sessionId}
            ORDER BY created_at
            """)
    List<ConversationRecord> findBySession(String sessionId);
}
