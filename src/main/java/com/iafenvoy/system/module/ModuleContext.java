package com.iafenvoy.system.module;

import com.iafenvoy.system.agent.AgentEngine;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.commerce.CommerceTransactions;
import com.iafenvoy.system.assistant.MinecraftTools;
import com.iafenvoy.system.data.SystemModule;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Per-server services supplied to feature installation, never retained in static module definitions.
 */
public final class ModuleContext {
    private final AgentEngine engine;
    private final MinecraftTools game;
    private final CommerceTransactions commerce;
    private final Registry<SystemModule> modules;
    private final Identifier id;
    private final Map<ToolRegistry.Type<?, ?>, ToolRegistry.Tool<?, ?>> tools = new HashMap<>();

    public ModuleContext(AgentEngine engine, MinecraftTools game, CommerceTransactions commerce, Registry<SystemModule> modules) {
        this(engine, game, commerce, modules, null);
    }

    private ModuleContext(AgentEngine engine, MinecraftTools game, CommerceTransactions commerce, Registry<SystemModule> modules, Identifier id) {
        this.engine = engine;
        this.game = game;
        this.commerce = commerce;
        this.modules = modules;
        this.id = id;
    }

    public AgentEngine engine() {
        return this.engine;
    }

    public MinecraftTools game() {
        return this.game;
    }

    public CommerceTransactions commerce() {
        return this.commerce;
    }

    public Registry<SystemModule> modules() {
        return this.modules;
    }

    public Identifier id() {
        return java.util.Objects.requireNonNull(this.id, "Module context is not scoped to a definition");
    }

    ModuleContext forModule(Identifier id) {
        return new ModuleContext(this.engine, this.game, this.commerce, this.modules, id);
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
