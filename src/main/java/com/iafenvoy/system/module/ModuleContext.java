package com.iafenvoy.system.module;

import com.iafenvoy.system.agent.AgentEngine;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.data.module.SystemModule;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Supplier;

/**
 * Per-server services supplied to feature installation, never retained in static module definitions.
 */
public final class ModuleContext {
    private final AgentEngine engine;
    private final Registry<SystemModule> modules;
    private final Map<ContextType<?>, Object> services;
    private final Identifier id;
    private final Map<Class<? extends PlayerModule>, PlayerModule> sharedRuntimes;
    private final Map<ToolRegistry.Type<?, ?>, ToolRegistry.Tool<?, ?>> tools = new HashMap<>();

    public ModuleContext(AgentEngine engine, Registry<SystemModule> modules) {
        this(engine, modules, new HashMap<>(), null, new LinkedHashMap<>());
    }

    private ModuleContext(AgentEngine engine, Registry<SystemModule> modules, Map<ContextType<?>, Object> services, Identifier id, Map<Class<? extends PlayerModule>, PlayerModule> sharedRuntimes) {
        this.engine = Objects.requireNonNull(engine);
        this.modules = Objects.requireNonNull(modules);
        this.services = services;
        this.id = id;
        this.sharedRuntimes = sharedRuntimes;
    }

    public AgentEngine engine() {
        return this.engine;
    }

    public Registry<SystemModule> modules() {
        return this.modules;
    }

    public <T> void put(@NotNull ContextType<T> type, @NotNull T value) {
        this.services.put(type, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(ContextType<T> type) {
        // Values enter only through put(), which associates each key with its declared type.
        return (T) Objects.requireNonNull(this.services.get(type), "Missing module context service: " + type);
    }

    public Identifier id() {
        return Objects.requireNonNull(this.id, "Module context is not scoped to a definition");
    }

    ModuleContext forModule(Identifier id) {
        return new ModuleContext(this.engine, this.modules, this.services, id, this.sharedRuntimes);
    }

    /**
     * Configured definitions may share a server-local runtime; ModuleManager owns its tick and close exactly once.
     */
    public <T extends PlayerModule> T sharedRuntime(Class<T> type, Supplier<T> factory) {
        return type.cast(this.sharedRuntimes.computeIfAbsent(type, _ -> Objects.requireNonNull(factory.get())));
    }

    List<PlayerModule> sharedRuntimes() {
        return List.copyOf(this.sharedRuntimes.values());
    }

    /**
     * Resolve an exported tool after ToolModule installation, without constructing another handler.
     */
    public <I, O> ToolRegistry.Tool<I, O> tool(ToolRegistry.Type<I, O> type) {
        return this.engine.tools().require(type);
    }

    public <I, O> ToolRegistry.Tool<I, O> tool(ToolRegistry.Type<I, O> type, Supplier<ToolRegistry.Tool<I, O>> factory) {
        ToolRegistry.Tool<?, ?> value = this.tools.computeIfAbsent(type, _ -> {
            ToolRegistry.Tool<I, O> tool = factory.get();
            if (tool.type() != type) throw new IllegalArgumentException("Incorrect tool factory " + type.id());
            return tool;
        });
        // The factory is checked once; shared workflows receive the same tool instance.
        @SuppressWarnings("unchecked") ToolRegistry.Tool<I, O> typed = (ToolRegistry.Tool<I, O>) value;
        return typed;
    }
}
