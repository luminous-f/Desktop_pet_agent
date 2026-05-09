package com.desktoppet.service.impl;

import com.desktoppet.entity.UserProfileRecord;
import com.desktoppet.dao.UserProfileMapper;
import com.desktoppet.service.ProfileService;
import org.springframework.stereotype.Service;

@Service
public class ProfileServiceImpl implements ProfileService {
    private final UserProfileMapper userProfileMapper;

    public ProfileServiceImpl(UserProfileMapper userProfileMapper) {
        this.userProfileMapper = userProfileMapper;
    }

    @Override
    public String currentProfile() {
        UserProfileRecord profile = userProfileMapper.current();
        return profile == null || profile.getProfileText() == null ? "" : profile.getProfileText();
    }

    @Override
    public void saveProfile(String profileText) {
        userProfileMapper.insert(profileText);
    }
}
