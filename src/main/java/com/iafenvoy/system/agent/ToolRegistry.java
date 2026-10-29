package com.iafenvoy.system.agent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Each tool performs one typed operation; modules register their complete tool lists together.
 */
public final class ToolRegistry {
    private final Map<String, Tool<?, ?>> tools = new HashMap<>();
    private final Map<String, Identifier> owners = new HashMap<>();
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
    public void registerAll(Identifier owner, List<Tool<?, ?>> values) {
        Objects.requireNonNull(owner);
        for (Tool<?, ?> tool : values) {
            Identifier previous = this.owners.get(tool.id());
            if (previous != null && !previous.equals(owner))
                throw new IllegalArgumentException("Conflicting tool owner " + tool.id());
        }
        this.registerAll(values);
        values.forEach(tool -> this.owners.put(tool.id(), owner));
    }

    public Identifier owner(String id) {
        return this.owners.get(id);
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
