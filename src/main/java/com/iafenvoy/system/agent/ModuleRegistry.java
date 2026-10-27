package com.iafenvoy.system.agent;

import com.iafenvoy.system.agent.routing.ModelRoute;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;

import java.util.*;
import java.util.stream.Collectors;

/**
 * The startup registry is the single source of module authority and all routing entry points.
 */
public final class ModuleRegistry {
    private final ToolRegistry tools;
    private final CommandRegistry commands = new CommandRegistry();
    private final Map<Identifier, Entry<?>> modules = new LinkedHashMap<>();
    private final Map<Identifier, RoutingProfile.Bound> profiles = new LinkedHashMap<>();
    private boolean frozen;

    public ModuleRegistry(ToolRegistry tools) {
        this.tools = tools;
    }

    public synchronized <I> void register(AgentModule<I> module) {
        this.checkMutable();
        AgentModule.Descriptor descriptor = module.descriptor();
        Identifier id = descriptor.id();
        if (this.modules.containsKey(id) || this.profiles.containsKey(id))
            throw new IllegalArgumentException("Duplicate module " + id);
        if (!id.equals(module.inputType().id()) || !ResultTypes.ACTIONS.contains(module.inputType()))
            throw new IllegalArgumentException("Module input type must be registered under its descriptor ID");
        List<ToolRegistry.Tool<?, ?>> operations = List.copyOf(module.tools());
        if (!operations.stream().map(ToolRegistry.Tool::id).collect(Collectors.toSet()).equals(descriptor.tools()))
            throw new IllegalArgumentException("Module tool list does not match its descriptor");
        ModuleRouting<I> routing = module.routing();
        if (!descriptor.interactive() && (!routing.commands().isEmpty() || routing.semantic().isPresent() || routing.shortcut().isPresent()))
            throw new IllegalArgumentException("Event-only modules cannot declare interactive entry points");
        // Explicit commands can authorize operations that remain absent from all automatic model routes.
        this.commands.validate(routing.commands());
        boolean readOnly = descriptor.interactive() && descriptor.policy().writeSteps() == 0
                && operations.stream().noneMatch(ToolRegistry.Tool::write);
        AgentResult.Action shortcut = routing.shortcut().map(value -> {
            TypedPayload<I> input = module.inputType().of(value.input());
            if (!readOnly) throw new IllegalArgumentException("Clarification shortcuts must be read-only");
            return new AgentResult.Action(value.labelKey(), input);
        }).orElse(null);
        RoutingProfile.Bound profile = routing.semantic().map(value -> value.bind(module.inputType(), readOnly && routing.modelSelectable())).orElse(null);
        // Every declaration is validated before any registry is changed; shared tools keep instance identity.
        this.tools.registerAll(operations);
        this.modules.put(id, new Entry<>(module, routing, readOnly, shortcut));
        this.commands.register(module.inputType(), routing.commands());
        if (profile != null) this.profiles.put(id, profile);
    }

    public synchronized void reject(Identifier id, RoutingProfile<?> profile) {
        this.checkMutable();
        if (profile.direct() || this.modules.containsKey(id) || this.profiles.containsKey(id))
            throw new IllegalArgumentException("Rejection examples require a unique non-callable ID");
        this.profiles.put(id, new RoutingProfile.Bound(id, profile.examples(), false, text -> null));
    }

    public synchronized void root(TypedPayload<?> input) {
        this.checkMutable();
        this.call(input);
        if (!this.readOnly(input.type().id())) throw new IllegalArgumentException("Root command must be read-only");
        this.commands.root(input);
    }

    public synchronized void freeze() {
        this.checkMutable();
        this.frozen = true;
        this.tools.freeze();
    }

    private void checkMutable() {
        if (this.frozen) throw new IllegalStateException("Agent registration has finished");
    }

    public AgentModule<?> get(Identifier id) {
        Entry<?> entry = this.modules.get(id);
        return entry == null ? null : entry.module();
    }

    public boolean readOnly(Identifier id) {
        Entry<?> entry = this.modules.get(id);
        return entry != null && entry.readOnly();
    }

    public boolean modelSelectable(Identifier id) {
        Entry<?> entry = this.modules.get(id);
        return entry != null && entry.module().descriptor().interactive() && entry.routing().modelSelectable();
    }

    public CommandRegistry commands() {
        return this.commands;
    }

    public Collection<AgentModule.Descriptor> descriptors() {
        return this.modules.values().stream().map(entry -> entry.module().descriptor()).toList();
    }

    public Collection<RoutingProfile.Bound> profiles() {
        return List.copyOf(this.profiles.values());
    }

    public List<AgentResult.Action> shortcuts() {
        return this.modules.values().stream().map(Entry::shortcut).filter(Objects::nonNull).toList();
    }

    public Map<String, ModelRoute.Capability> capabilities() {
        Map<String, ModelRoute.Capability> capabilities = new LinkedHashMap<>();
        this.modules.forEach((id, entry) -> {
            if (this.modelSelectable(id)) {
                AgentModule.Descriptor descriptor = entry.module().descriptor();
                capabilities.put(id.toString(), new ModelRoute.Capability(descriptor.description(), descriptor.schema()));
            }
        });
        return Collections.unmodifiableMap(capabilities);
    }

    public Codec<ModelRoute> modelCodec() {
        List<TypedPayload.Type<?>> capabilities = new ArrayList<>();
        for (Entry<?> entry : this.modules.values())
            if (this.modelSelectable(entry.module().descriptor().id())) capabilities.add(entry.module().inputType());
        return ModelRoute.codec(capabilities);
    }

    public <I> Call<I> call(TypedPayload<I> input) {
        AgentModule<?> module = this.get(input.type().id());
        if (module == null) throw new AgentException("CAPABILITY_UNAVAILABLE");
        if (module.inputType() != input.type()) throw new AgentException("INVALID_INPUT");
        // Registration ties this exact type key to I; keep the erased cast at this one boundary.
        @SuppressWarnings("unchecked") AgentModule<I> typed = (AgentModule<I>) module;
        return new Call<>(typed, input.value());
    }

    private record Entry<I>(AgentModule<I> module, ModuleRouting<I> routing, boolean readOnly,
                            AgentResult.Action shortcut) {
    }

    public record Call<I>(AgentModule<I> module, I input) {
        public AgentResult execute(ExecutionContext context) throws Exception {
            return this.module.execute(context, this.input);
        }
    }
}
