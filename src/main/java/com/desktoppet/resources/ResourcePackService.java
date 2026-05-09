package com.desktoppet.resources;

import com.desktoppet.entity.ResourcePackRecord;
import com.desktoppet.dao.ResourcePackMapper;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Service
public class ResourcePackService {
    private final ResourcePackMapper resourcePackMapper;

    public ResourcePackService(ResourcePackMapper resourcePackMapper) {
        this.resourcePackMapper = resourcePackMapper;
    }

    public List<ResourcePack> enabledPacks() {
        try {
            return resourcePackMapper.enabledPacks().stream()
                    .map(this::toPack)
                    .toList();
        } catch (Exception ignored) {
        }
        return List.of();
    }

    public void addPack(ResourcePack pack) {
        ResourcePackRecord record = new ResourcePackRecord();
        record.setName(pack.name());
        record.setRootPath(pack.rootPath().toString());
        record.setPersonaPath(pack.personaPath() == null ? null : pack.personaPath().toString());
        record.setWorldPath(pack.worldPath() == null ? null : pack.worldPath().toString());
        record.setEnabled(pack.enabled());
        resourcePackMapper.insert(record);
    }

    private ResourcePack toPack(ResourcePackRecord record) {
        return new ResourcePack(
                record.getName(),
                Path.of(record.getRootPath()),
                nullablePath(record.getPersonaPath()),
                nullablePath(record.getWorldPath()),
                record.isEnabled()
        );
    }

    private Path nullablePath(String value) {
        return value == null || value.isBlank() ? null : Path.of(value);
    }
}
