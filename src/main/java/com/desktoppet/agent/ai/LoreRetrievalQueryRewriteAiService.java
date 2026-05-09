package com.desktoppet.agent.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface LoreRetrievalQueryRewriteAiService {
    @SystemMessage("""
            Rewrite the user's lore, story, character, or worldbuilding question into a direct retrieval query.
            Resolve pronouns and role references using the user profile and character context.
            Keep the query short and factual. Do not answer the question.
            Output only the rewritten query text, without JSON, quotes, labels, or explanation.
            """)
    @UserMessage("""
            User profile:
            {{userProfile}}

            Character context:
            {{characterContext}}

            User message:
            {{userMessage}}
            """)
    String rewrite(
            @V("userProfile") String userProfile,
            @V("characterContext") String characterContext,
            @V("userMessage") String userMessage
    );
}
