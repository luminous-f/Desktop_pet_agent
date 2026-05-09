package com.desktoppet.resources;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class CharacterContextService {
    private static final List<String> DEFAULT_CONTEXT_RESOURCES = List.of(
            "/assets/character/Castorice/persona.md",
            "/assets/character/Castorice/world.md",
            "/assets/character/Castorice/background.md"
    );

    public String loadDefaultContext() {
        StringBuilder context = new StringBuilder();
        for (String resource : DEFAULT_CONTEXT_RESOURCES) {
            String text = readResource(resource);
            if (!text.isBlank()) {
                context.append("\n\n").append(text);
            }
        }
        return context.toString().trim();
    }

    private String readResource(String resource) {
        try (InputStream in = CharacterContextService.class.getResourceAsStream(resource)) {
            if (in == null) {
                return "";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
