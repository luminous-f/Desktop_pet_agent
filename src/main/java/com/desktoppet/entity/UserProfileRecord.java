package com.desktoppet.entity;

public final class UserProfileRecord {
    private long id;
    private String profileText;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getProfileText() {
        return profileText;
    }

    public void setProfileText(String profileText) {
        this.profileText = profileText;
    }
}
