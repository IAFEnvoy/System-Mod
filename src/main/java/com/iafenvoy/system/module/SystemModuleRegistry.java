package com.iafenvoy.system.module;

import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SystemModuleRegistry {
    private final Map<Identifier, SystemModule> modules = new LinkedHashMap<>();
    private boolean frozen;

    public void register(SystemModule module) {
        if (this.frozen) throw new IllegalStateException("System module registration has finished");
        if (this.modules.putIfAbsent(module.id(), module) != null)
            throw new IllegalArgumentException("Duplicate system module " + module.id());
    }

    public List<SystemModule> values() {
        return List.copyOf(this.modules.values());
    }

    void freeze() {
        this.frozen = true;
    }
}
