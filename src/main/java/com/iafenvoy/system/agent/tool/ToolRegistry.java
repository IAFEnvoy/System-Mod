package com.iafenvoy.system.agent.tool;

import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
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
        values.forEach(tool -> this.owners.computeIfAbsent(tool.id(), unused1 -> new LinkedHashSet<>()).add(owner));
    }

    public void registerVariant(ResourceLocation owner, List<Tool<?, ?>> values) {
        if (owner == null) {
            this.registerAll(values);
            return;
        }
        if (this.frozen) throw new IllegalStateException("Agent registration has finished");
        Map<String, Tool<?, ?>> scoped = this.variants.computeIfAbsent(owner, unused2 -> new HashMap<>());
        for (Tool<?, ?> tool : values) {
            Tool<?, ?> previous = this.tools.get(tool.id());
            if (previous != null && (previous.type() != tool.type() || previous.write() != tool.write()
                    || previous.idempotent() != tool.idempotent() || previous.allowEvent() != tool.allowEvent()
                    || previous.priority() != tool.priority()))
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

    public void freeze() {
        this.frozen = true;
    }

    public boolean contains(String id) {
        return this.tools.containsKey(id);
    }

    public boolean writes(String id) {
        Tool<?, ?> tool = this.tools.get(id);
        if (tool == null) throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
        return tool.write();
    }

    /**
     * Only explicitly advertised read operations enter general reasoning, using an enabled owner's handler.
     */
    public Map<String, ScopedTool<?, ?>> reasoning(Predicate<ResourceLocation> available) {
        return this.advertised(available, tool -> !tool.reasoningDescription().isBlank() && tool.priority() != ToolPriority.PAID);
    }

    /** Provider additions only widen the read catalogue; owners, exact type identity and schemas still apply. */
    public Map<String, ScopedTool<?, ?>> chat(Predicate<ResourceLocation> available, Collection<Type<?, ?>> additions) {
        Set<Type<?, ?>> selected = Collections.newSetFromMap(new IdentityHashMap<>());
        selected.addAll(additions);
        return this.advertised(available, tool -> !tool.write() && tool.type().inputCodec() instanceof SchemaMapCodec<?>
                && (!tool.reasoningDescription().isBlank() || selected.contains(tool.type())));
    }

    private Map<String, ScopedTool<?, ?>> advertised(Predicate<ResourceLocation> available, Predicate<Tool<?, ?>> select) {
        Map<String, ScopedTool<?, ?>> result = new LinkedHashMap<>();
        this.variants.forEach((owner, values) -> {
            if (!available.test(owner)) return;
            values.values().stream().sorted(Comparator.comparing(Tool::id)).filter(select)
                    .forEach(tool -> result.putIfAbsent(tool.id(), scoped(owner, tool)));
        });
        this.tools.values().stream().sorted(Comparator.comparing(Tool::id))
                .filter(tool -> select.test(tool) && !this.owners.containsKey(tool.id())
                        && this.variants.values().stream().noneMatch(values -> values.containsKey(tool.id())))
                .forEach(tool -> result.putIfAbsent(tool.id(), scoped(null, tool)));
        return Collections.unmodifiableMap(result);
    }

    private static <I, O> ScopedTool<I, O> scoped(ResourceLocation owner, Tool<I, O> tool) {
        return new ScopedTool<>(owner, tool);
    }

    public record ScopedTool<I, O>(ResourceLocation owner, Tool<I, O> tool) {
    }

    // Identity checks keep a caller-created key or handler from replacing an authorized operation.
    @SuppressWarnings("unchecked")
    public <I, O> Tool<I, O> require(Type<I, O> type) {
        Tool<?, ?> tool = this.tools.get(type.id());
        if (tool == null) throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
        if (tool.type() != type) throw new AgentException(AgentFailure.NOT_AUTHORIZED);
        return (Tool<I, O>) tool;
    }

    public <I, O> Tool<I, O> require(Type<I, O> type, ResourceLocation owner, Predicate<ResourceLocation> available) {
        return this.scope(type, owner, available).tool();
    }

    public <I, O> ScopedTool<I, O> scope(Type<I, O> type, ResourceLocation owner, Predicate<ResourceLocation> available) {
        Set<ResourceLocation> exporting = this.owners.get(type.id());
        ResourceLocation selectedOwner = exporting == null ? owner : exporting.stream().filter(available)
                .filter(id -> this.variants.get(id).containsKey(type.id())).findFirst().orElse(null);
        Tool<?, ?> tool = exporting != null && selectedOwner == null ? null
                : this.variants.getOrDefault(selectedOwner, Map.of()).get(type.id());
        if (tool == null && exporting == null) {
            tool = this.tools.get(type.id());
            selectedOwner = null;
        }
        if (tool == null) throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
        if (tool.type() != type) throw new AgentException(AgentFailure.NOT_AUTHORIZED);
        @SuppressWarnings("unchecked") Tool<I, O> typed = (Tool<I, O>) tool;
        return new ScopedTool<>(selectedOwner, typed);
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
                             Handler<I, O> handler, String reasoningDescription, ToolPriority priority) {
        public Tool {
            Objects.requireNonNull(type);
            Objects.requireNonNull(handler);
            Objects.requireNonNull(reasoningDescription);
            Objects.requireNonNull(priority);
            if (!reasoningDescription.isBlank() && (write || !(type.inputCodec() instanceof SchemaMapCodec<?>)))
                throw new IllegalArgumentException("Reasoning tools must be read-only and provide an input schema");
        }

        public Tool(Type<I, O> type, boolean write, boolean idempotent, boolean allowEvent, Handler<I, O> handler) {
            this(type, write, idempotent, allowEvent, handler, "", ToolPriority.LOCAL);
        }

        public Tool<I, O> forReasoning(String description) {
            return new Tool<>(this.type, this.write, this.idempotent, this.allowEvent, this.handler, description, this.priority);
        }

        public Tool<I, O> withPriority(ToolPriority priority) {
            return new Tool<>(this.type, this.write, this.idempotent, this.allowEvent, this.handler, this.reasoningDescription, priority);
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
