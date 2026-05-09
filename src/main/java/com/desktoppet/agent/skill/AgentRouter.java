package com.desktoppet.agent.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class AgentRouter {
    private final ObjectMapper mapper = new ObjectMapper();
    private final OpenAiChatModel model;

    public AgentRouter(OpenAiChatModel model) {
        this.model = model;
    }

    public RouteDecision route(String userMessage, String pendingState) {
        if (model == null) {
            return RouteDecision.chat();
        }
        String prompt = """
                You are an intent router for a desktop pet agent. Decide whether to call a skill.
                Return only strict JSON. Do not include markdown.

                Actions:
                - chat: normal conversation, no tool.
                - skill: call exactly one skill.
                - clarify: ask for missing required information.

                Skills:
                - weather: Use when the user asks weather, temperature, going out, clothes, umbrella, wind, humidity, or travel clothing advice. Arguments: {}
                - news: Use when the user asks for today's/latest important news summary without a specific search target. Arguments: {}
                - search: Use when the user asks to search/look up/check recent/current information about a specific person, company, stock, product, event, concept, or topic. Arguments: {"query":"full search query"}
                - schedule: Use for today's schedule query or adding a schedule/reminder. Arguments: {"op":"query_today|add|confirm_add","title":"..."}
                - file_organize: Use for organizing, classifying, scanning, reading, or categorizing files/literature/PDFs/documents. Arguments: {"op":"preview|confirm","sourceRoot":"path if present","extensions":"pdf by default","instruction":"full user requirement"}
                - allowed_roots: Use when the user wants to set/update/replace file organization whitelist or allowed directory. Arguments: {"roots":"semicolon separated paths"}
                - profile: Use when the user asks to show/read/update/edit user profile. Arguments: {"op":"query|replace","profileText":"..."}

                Confirmation rules:
                - Adding schedule requires schedule op=add first; a later confirmation can use op=confirm_add.
                - File organization must preview first; a later confirmation can use file_organize op=confirm.
                - Whitelist update can execute directly.
                - Profile auto-update is handled separately. Use profile only for explicit user requests.

                Pending state:
                %s

                JSON shape:
                {
                  "action": "chat|skill|clarify",
                  "skill": "weather|news|search|schedule|file_organize|allowed_roots|profile",
                  "arguments": {"key":"value"},
                  "clarification": "question if action=clarify",
                  "confirmationText": "confirmation wording if needed"
                }

                User message:
                %s
                """.formatted(pendingState == null ? "none" : pendingState, userMessage);
        try {
            return parse(model.chat(prompt));
        } catch (Exception e) {
            return RouteDecision.chat();
        }
    }

    private RouteDecision parse(String raw) throws IOException {
        String text = raw == null ? "" : raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            text = text.substring(start, end + 1);
        }
        JsonNode root = mapper.readTree(text);
        Map<String, String> arguments = new HashMap<>();
        JsonNode args = root.path("arguments");
        if (args.isObject()) {
            args.fields().forEachRemaining(entry -> arguments.put(entry.getKey(), entry.getValue().asText("")));
        }
        return new RouteDecision(
                root.path("action").asText("chat"),
                root.path("skill").asText(""),
                Map.copyOf(arguments),
                root.path("clarification").asText(""),
                root.path("confirmationText").asText("")
        );
    }
}
