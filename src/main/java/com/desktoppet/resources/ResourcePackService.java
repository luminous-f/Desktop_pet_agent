package com.desktoppet.resources;

import com.desktoppet.storage.Database;
import com.desktoppet.storage.entity.ResourcePackRecord;
import com.desktoppet.storage.mapper.ResourcePackMapper;
import org.apache.ibatis.session.SqlSession;

import java.nio.file.Path;
import java.util.List;

public final class ResourcePackService {
    private final Database database;

    public ResourcePackService(Database database) {
        this.database = database;
    }

    public List<ResourcePack> enabledPacks() {
        try (SqlSession session = database.openSession()) {
            return session.getMapper(ResourcePackMapper.class).enabledPacks().stream()
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
        try (SqlSession session = database.openSession()) {
            session.getMapper(ResourcePackMapper.class).insert(record);
        }
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
