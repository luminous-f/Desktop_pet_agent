package com.desktoppet.dao;

import com.desktoppet.entity.UserProfileRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

public interface UserProfileMapper {
    @Select("SELECT id, profile_text FROM user_profile ORDER BY updated_at DESC LIMIT 1")
    UserProfileRecord current();

    @Insert("INSERT INTO user_profile(profile_text) VALUES (#{profileText})")
    void insert(String profileText);
}
