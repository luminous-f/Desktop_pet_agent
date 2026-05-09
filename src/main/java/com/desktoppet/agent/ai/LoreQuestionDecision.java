package com.desktoppet.agent.ai;

public record LoreQuestionDecision(boolean loreRelated, String category, String reason) {
    public static LoreQuestionDecision notLore(String reason) {
        return new LoreQuestionDecision(false, "none", reason);
    }
}
