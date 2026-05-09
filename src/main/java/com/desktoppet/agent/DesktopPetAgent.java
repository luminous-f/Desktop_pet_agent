package com.desktoppet.agent;

public interface DesktopPetAgent {
    String reply(String userMessage);

    String proactiveTopic();

    default String startupNotice() {
        return "";
    }

    default void shutdown() {
    }
}
