package com.desktoppet.agent.ai;

public record ProfileUpdateDecision(boolean shouldUpdate, String profileText, String reason) {
}
