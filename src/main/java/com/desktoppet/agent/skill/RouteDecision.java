package com.desktoppet.agent.skill;

import java.util.Map;

public record RouteDecision(
        String action,
        String skill,
        Map<String, String> arguments,
        String clarification,
        String confirmationText
) {
    public static RouteDecision chat() {
        return new RouteDecision("chat", "", Map.of(), "", "");
    }
}
