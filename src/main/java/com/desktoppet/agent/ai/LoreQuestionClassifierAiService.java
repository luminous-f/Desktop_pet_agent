package com.desktoppet.agent.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface LoreQuestionClassifierAiService {
    @SystemMessage("""
            Classify whether the user is asking about fictional story, character facts,
            character relationships, worldbuilding, background lore, plot events, dialogue meaning,
            motives, chronology, or canon setting.
            Return loreRelated=true for those questions even if the wording is indirect.
            Use the user profile to resolve pronouns. For example, if the user profile says the user
            is the Trailblazer/player, then "you and me" can mean the character and the Trailblazer.
            If category is story, character, character relationship, worldbuilding, lore, plot, canon,
            dialogue, motive, or chronology, loreRelated must be true.
            Return loreRelated=false for ordinary chat, tool requests, weather, news, schedules,
            file organization, user profile questions, or general advice.
            Do not answer the user. Only classify.
            """)
    @UserMessage("""
            User profile:
            {{userProfile}}

            User message:
            {{userMessage}}
            """)
    LoreQuestionDecision classify(
            @V("userProfile") String userProfile,
            @V("userMessage") String userMessage
    );
}
