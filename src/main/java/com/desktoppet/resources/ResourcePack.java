package com.desktoppet.resources;

import java.nio.file.Path;

public record ResourcePack(
        String name,
        Path rootPath,
        Path personaPath,
        Path worldPath,
        boolean enabled
) {
}
