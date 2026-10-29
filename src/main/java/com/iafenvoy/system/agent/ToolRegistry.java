package com.iafenvoy.system.agent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;

/**
 * Each tool performs one typed operation; modules register their complete tool lists together.
 */
public final class ToolRegistry {
    private final Map<String, Tool<?, ?>> tools = new HashMap<>();
    private final Map<String, Set<ResourceLocation>> owners = new HashMap<>();
    private final Map<ResourceLocation, Map<String, Tool<?, ?>>> variants = new LinkedHashMap<>();
    private boolean frozen;

    public void registerAll(List<Tool<?, ?>> values) {
        if (this.frozen) throw new IllegalStateException("Agent registration has finished");
        Map<String, Tool<?, ?>> pending = new HashMap<>();
        for (Tool<?, ?> tool : values) {
            Tool<?, ?> previous = pending.putIfAbsent(tool.id(), tool);
            if (previous != null && previous != tool || this.tools.containsKey(tool.id()) && this.tools.get(tool.id()) != tool)
                throw new IllegalArgumentException("Conflicting tool " + tool.id());
        }
        this.tools.putAll(pending);
    }

    /**
     * Exported operations keep their feature owner even when several workflows share the same handler.
     */
    public void registerAll(ResourceLocation owner, List<Tool<?, ?>> values) {
        Objects.requireNonNull(owner);
        this.registerVariant(owner, values);
        values.forEach(tool -> this.owners.computeIfAbsent(tool.id(), unused -> new LinkedHashSet<>()).add(owner));
    }

    public void registerVariant(ResourceLocation owner, List<Tool<?, ?>> values) {
        if (owner == null) {
            this.registerAll(values);
            return;
        }
        if (this.frozen) throw new IllegalStateException("Agent registration has finished");
        Map<String, Tool<?, ?>> scoped = this.variants.computeIfAbsent(owner, unused -> new HashMap<>());
        for (Tool<?, ?> tool : values) {
            Tool<?, ?> previous = this.tools.get(tool.id());
            if (previous != null && (previous.type() != tool.type() || previous.write() != tool.write()
                    || previous.idempotent() != tool.idempotent() || previous.allowEvent() != tool.allowEvent()))
                throw new IllegalArgumentException("Conflicting tool type " + tool.id());
            Tool<?, ?> existing = scoped.putIfAbsent(tool.id(), tool);
            if (existing != null && existing != tool)
                throw new IllegalArgumentException("Conflicting tool " + tool.id() + " for " + owner);
            this.tools.putIfAbsent(tool.id(), tool);
        }
    }

    public boolean available(String id, Predicate<ResourceLocation> available) {
        Set<ResourceLocation> exporting = this.owners.get(id);
        return this.tools.containsKey(id) && (exporting == null || exporting.stream().anyMatch(available));
    }

    void freeze() {
        this.frozen = true;
    }

    public boolean contains(String id) {
        return this.tools.containsKey(id);
    }

    public boolean writes(String id) {
        Tool<?, ?> tool = this.tools.get(id);
        if (tool == null) throw new AgentException("CAPABILITY_UNAVAILABLE");
        return tool.write();
    }

    // Identity checks keep a caller-created key or handler from replacing an authorized operation.
    @SuppressWarnings("unchecked")
    public <I, O> Tool<I, O> require(Type<I, O> type) {
        Tool<?, ?> tool = this.tools.get(type.id());
        if (tool == null) throw new AgentException("CAPABILITY_UNAVAILABLE");
        if (tool.type() != type) throw new AgentException("NOT_AUTHORIZED");
        return (Tool<I, O>) tool;
    }

    public <I, O> Tool<I, O> require(Type<I, O> type, ResourceLocation owner, Predicate<ResourceLocation> available) {
        Set<ResourceLocation> exporting = this.owners.get(type.id());
        Tool<?, ?> tool = exporting == null ? this.variants.getOrDefault(owner, Map.of()).get(type.id())
                : exporting.stream().filter(available).map(id -> this.variants.get(id).get(type.id()))
                        .filter(Objects::nonNull).findFirst().orElse(null);
        if (tool == null && exporting == null) tool = this.tools.get(type.id());
        if (tool == null) throw new AgentException("CAPABILITY_UNAVAILABLE");
        if (tool.type() != type) throw new AgentException("NOT_AUTHORIZED");
        @SuppressWarnings("unchecked") Tool<I, O> typed = (Tool<I, O>) tool;
        return typed;
    }

    public record Type<I, O>(String id, MapCodec<I> inputCodec, Codec<O> resultCodec) {
        public Type {
            if (id == null || !id.matches("[a-z0-9_.-]+(?:[.:][a-z0-9_./-]+)+"))
                throw new IllegalArgumentException("Invalid tool ID");
            Objects.requireNonNull(inputCodec);
            Objects.requireNonNull(resultCodec);
        }
    }

    public record Tool<I, O>(Type<I, O> type, boolean write, boolean idempotent, boolean allowEvent,
                             Handler<I, O> handler) {
        public Tool {
            Objects.requireNonNull(type);
            Objects.requireNonNull(handler);
        }

        public String id() {
            return this.type.id();
        }
    }

    @FunctionalInterface
    public interface Handler<I, O> {
        CompletionStage<O> execute(ExecutionContext context, I input);
    }
}
