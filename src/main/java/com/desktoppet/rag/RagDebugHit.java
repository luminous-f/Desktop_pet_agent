package com.desktoppet.rag;

public record RagDebugHit(
        int rank,
        String source,
        String parentId,
        String vectorScore,
        double dialogueBoost,
        double weightedScore,
        String boostReason,
        int textLength,
        String text
) {
}
