package com.desktoppet.dao;

import com.desktoppet.entity.ResourcePackRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ResourcePackMapper {
    @Select("SELECT name, root_path, persona_path, world_path, enabled FROM resource_packs WHERE enabled = TRUE")
    List<ResourcePackRecord> enabledPacks();

    @Insert("""
            INSERT INTO resource_packs(name, root_path, persona_path, world_path, enabled)
            VALUES (#{name}, #{rootPath}, #{personaPath}, #{worldPath}, #{enabled})
            """)
    void insert(ResourcePackRecord pack);
}
