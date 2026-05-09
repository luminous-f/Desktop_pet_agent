package com.desktoppet.storage.entity;

public final class ResourcePackRecord {
    private String name;
    private String rootPath;
    private String personaPath;
    private String worldPath;
    private boolean enabled;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public String getPersonaPath() {
        return personaPath;
    }

    public void setPersonaPath(String personaPath) {
        this.personaPath = personaPath;
    }

    public String getWorldPath() {
        return worldPath;
    }

    public void setWorldPath(String worldPath) {
        this.worldPath = worldPath;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
