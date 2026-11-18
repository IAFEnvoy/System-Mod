package com.iafenvoy.system.agent.tool;

import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.module.ModuleScope;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;

/**
 * Each tool performs one typed operation; modules register their complete tool lists together.
 */
public final class ToolRegistry {
    private final Map<String, Tool<?, ?>> tools = new HashMap<>();
    private final Map<String, Tool<?, ?>> globals = new HashMap<>();
    private final Map<String, Set<ModuleScope>> owners = new HashMap<>();
    private final Map<ModuleScope, Map<String, Tool<?, ?>>> variants = new LinkedHashMap<>();
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
        this.globals.putAll(pending);
    }

    /**
     * Exported operations keep their feature owner even when several workflows share the same handler.
     */
    public void registerAll(Identifier owner, List<Tool<?, ?>> values) {
        this.registerExported(new ModuleScope.Definition(owner), values);
    }

    public void registerExported(ModuleScope scope, List<Tool<?, ?>> values) {
        this.registerScoped(scope, values);
        values.forEach(tool -> this.owners.computeIfAbsent(tool.id(), _ -> new LinkedHashSet<>()).add(scope));
    }

    public void registerVariant(Identifier owner, List<Tool<?, ?>> values) {
        this.registerScoped(ModuleScope.of(owner), values);
    }

    public void registerScoped(ModuleScope scope, List<Tool<?, ?>> values) {
        Objects.requireNonNull(scope);
        if (scope == ModuleScope.Global.INSTANCE) {
            this.registerAll(values);
            return;
        }
        if (this.frozen) throw new IllegalStateException("Agent registration has finished");
        Map<String, Tool<?, ?>> scoped = this.variants.computeIfAbsent(scope, _ -> new HashMap<>());
        for (Tool<?, ?> tool : values) {
            Tool<?, ?> previous = this.tools.get(tool.id());
            if (previous != null && (previous.type() != tool.type() || previous.write() != tool.write()
                    || previous.idempotent() != tool.idempotent() || previous.allowEvent() != tool.allowEvent()
                    || previous.priority() != tool.priority()))
                throw new IllegalArgumentException("Conflicting tool type " + tool.id());
            Tool<?, ?> existing = scoped.putIfAbsent(tool.id(), tool);
            if (existing != null && existing != tool)
                throw new IllegalArgumentException("Conflicting tool " + tool.id() + " for " + scope);
            this.tools.putIfAbsent(tool.id(), tool);
        }
    }

    public boolean available(String id, Predicate<Identifier> available) {
        if (this.globals.containsKey(id)) return true;
        Set<ModuleScope> exporting = this.owners.get(id);
        if (exporting != null) return exporting.stream().anyMatch(scope -> scope.available(available));
        return this.variants.entrySet().stream().anyMatch(entry -> entry.getKey().available(available) && entry.getValue().containsKey(id));
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
    public Map<String, ScopedTool<?, ?>> reasoning(Predicate<Identifier> available) {
        return this.advertised(available, tool -> !tool.reasoningDescription().isBlank() && tool.priority() != ToolPriority.PAID);
    }

    /**
     * Provider additions only widen the read catalogue; owners, exact type identity and schemas still apply.
     */
    public Map<String, ScopedTool<?, ?>> chat(Predicate<Identifier> available, Collection<Type<?, ?>> additions) {
        Set<Type<?, ?>> selected = Collections.newSetFromMap(new IdentityHashMap<>());
        selected.addAll(additions);
        return this.advertised(available, tool -> !tool.write() && tool.type().inputCodec() instanceof SchemaMapCodec<?>
                && (!tool.reasoningDescription().isBlank() || selected.contains(tool.type())));
    }

    private Map<String, ScopedTool<?, ?>> advertised(Predicate<Identifier> available, Predicate<Tool<?, ?>> select) {
        Map<String, ScopedTool<?, ?>> result = new LinkedHashMap<>();
        this.variants.forEach((owner, values) -> {
            if (!owner.available(available)) return;
            values.values().stream().sorted(Comparator.comparing(Tool::id)).filter(select)
                    .forEach(tool -> result.putIfAbsent(tool.id(), scoped(owner, tool)));
        });
        this.globals.values().stream().sorted(Comparator.comparing(Tool::id))
                .filter(select)
                .forEach(tool -> result.putIfAbsent(tool.id(), scoped(ModuleScope.Global.INSTANCE, tool)));
        return Collections.unmodifiableMap(result);
    }

    private static <I, O> ScopedTool<I, O> scoped(ModuleScope owner, Tool<I, O> tool) {
        return new ScopedTool<>(owner, tool);
    }

    public record ScopedTool<I, O>(ModuleScope scope, Tool<I, O> tool) {
    }

    // Identity checks keep a caller-created key or handler from replacing an authorized operation.
    @SuppressWarnings("unchecked")
    public <I, O> Tool<I, O> require(Type<I, O> type) {
        Tool<?, ?> tool = this.tools.get(type.id());
        if (tool == null) throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
        if (tool.type() != type) throw new AgentException(AgentFailure.NOT_AUTHORIZED);
        return (Tool<I, O>) tool;
    }

    public <I, O> Tool<I, O> require(Type<I, O> type, ModuleScope scope, Predicate<Identifier> available) {
        return this.scope(type, scope, available).tool();
    }

    public <I, O> ScopedTool<I, O> scope(Type<I, O> type, ModuleScope scope, Predicate<Identifier> available) {
        Tool<?, ?> own = this.variants.getOrDefault(scope, Map.of()).get(type.id());
        if (own != null && scope.available(available)) return checked(scope, type, own);
        for (ModuleScope exporting : this.owners.getOrDefault(type.id(), Set.of())) {
            Tool<?, ?> tool = this.variants.getOrDefault(exporting, Map.of()).get(type.id());
            if (tool != null && exporting.available(available)) return checked(exporting, type, tool);
        }
        Tool<?, ?> global = this.globals.get(type.id());
        if (global != null) return checked(ModuleScope.Global.INSTANCE, type, global);
        throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
    }

    /**
     * Member dispatch never falls back to another member or a globally exported handler.
     */
    public <I, O> ScopedTool<I, O> member(Type<I, O> type, Identifier owner, Predicate<Identifier> available) {
        ModuleScope scope = new ModuleScope.Definition(owner);
        Tool<?, ?> tool = this.variants.getOrDefault(scope, Map.of()).get(type.id());
        if (tool == null || !scope.available(available)) throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
        return checked(scope, type, tool);
    }

    @SuppressWarnings("unchecked")
    private static <I, O> ScopedTool<I, O> checked(ModuleScope scope, Type<I, O> type, Tool<?, ?> tool) {
        if (tool.type() != type) throw new AgentException(AgentFailure.NOT_AUTHORIZED);
        return new ScopedTool<>(scope, (Tool<I, O>) tool);
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
