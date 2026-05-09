package com.desktoppet.service;

public interface ProfileService {
    String currentProfile();

    void saveProfile(String profileText);
}
