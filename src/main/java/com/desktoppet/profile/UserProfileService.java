package com.desktoppet.profile;

import com.desktoppet.storage.Database;
import com.desktoppet.storage.entity.UserProfileRecord;
import com.desktoppet.storage.mapper.UserProfileMapper;
import org.apache.ibatis.session.SqlSession;

public final class UserProfileService {
    private final Database database;

    public UserProfileService(Database database) {
        this.database = database;
    }

    public String currentProfile() {
        try (SqlSession session = database.openSession()) {
            UserProfileRecord profile = session.getMapper(UserProfileMapper.class).current();
            return profile == null || profile.getProfileText() == null ? "" : profile.getProfileText();
        }
    }

    public void saveProfile(String profileText) {
        try (SqlSession session = database.openSession()) {
            session.getMapper(UserProfileMapper.class).insert(profileText);
        }
    }
}
