package com.iafenvoy.system.agent;

import com.iafenvoy.system.agent.routing.ModelRoute;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.registry.SystemRegistries;
import com.iafenvoy.system.util.ActionArguments;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The startup registry is the single source of module authority and all routing entry points.
 */
public final class ModuleRegistry {
    private final ToolRegistry tools;
    private final CommandRegistry commands = new CommandRegistry();
    private final Map<Identifier, Entry<?>> modules = new LinkedHashMap<>();
    private final Map<Identifier, Map<Identifier, AgentModule<?>>> variants = new LinkedHashMap<>();
    private final Map<Identifier, RoutingProfile.Bound> profiles = new LinkedHashMap<>();
    private boolean frozen;

    public ModuleRegistry(ToolRegistry tools) {
        this.tools = tools;
    }

    public synchronized <I extends AgentAction> void register(AgentModule<I> module) {
        this.register(null, module);
    }

    public synchronized <I extends AgentAction> void register(Identifier owner, AgentModule<I> module) {
        this.checkMutable();
        AgentModule.Descriptor descriptor = module.descriptor();
        Identifier id = descriptor.id();
        if (this.modules.containsKey(id)) {
            Entry<?> previous = this.modules.get(id);
            if (owner == null || this.variants.get(id).containsKey(owner)
                    || previous.module().inputCodec() != module.inputCodec()
                    || previous.module().getClass() != module.getClass()
                    || !previous.module().descriptor().tools().equals(descriptor.tools())
                    || descriptor.version() != previous.module().descriptor().version()
                    || descriptor.interactive() != previous.module().descriptor().interactive()
                    || descriptor.event() != previous.module().descriptor().event()
                    || !module.tools().stream().map(ToolRegistry.Tool::id).collect(Collectors.toSet()).equals(descriptor.tools())
                    || previous.readOnly() != (descriptor.interactive() && descriptor.policy().writeSteps() == 0
                        && module.tools().stream().noneMatch(ToolRegistry.Tool::write)))
                throw new IllegalArgumentException("Conflicting workflow variant " + id + " for " + owner);
            this.tools.registerVariant(owner, module.tools());
            this.variants.get(id).put(owner, module);
            return;
        }
        if (this.profiles.containsKey(id))
            throw new IllegalArgumentException("Duplicate module " + id);
        if (SystemRegistries.ACTION_TYPE.getValue(id) != module.inputCodec())
            throw new IllegalArgumentException("Module input type must be registered under its descriptor ID");
        List<ToolRegistry.Tool<?, ?>> operations = List.copyOf(module.tools());
        if (!operations.stream().map(ToolRegistry.Tool::id).collect(Collectors.toSet()).equals(descriptor.tools()))
            throw new IllegalArgumentException("Module tool list does not match its descriptor");
        ModuleRouting<I> routing = module.routing();
        if (!descriptor.interactive() && (!routing.commands().isEmpty() || routing.semantic().isPresent() || routing.shortcut().isPresent() || routing.explicit().isPresent()))
            throw new IllegalArgumentException("Event-only modules cannot declare interactive entry points");
        // Explicit commands can authorize operations that remain absent from all automatic model routes.
        this.commands.validate(routing.commands());
        boolean readOnly = descriptor.interactive() && descriptor.policy().writeSteps() == 0
                && operations.stream().noneMatch(ToolRegistry.Tool::write);
        AgentResult.Action shortcut = routing.shortcut().map(value -> {
            I input = AgentAction.validate(module.inputCodec(), value.input());
            if (!readOnly) throw new IllegalArgumentException("Clarification shortcuts must be read-only");
            return new AgentResult.Action(value.labelKey(), input);
        }).orElse(null);
        RoutingProfile.Bound profile = routing.semantic().map(value -> value.bind(id, module.inputCodec(), readOnly && routing.semanticSelectable())).orElse(null);
        // Every declaration is validated before any registry is changed; shared tools keep instance identity.
        this.tools.registerVariant(owner, operations);
        this.modules.put(id, new Entry<>(module, routing, readOnly, shortcut));
        Map<Identifier, AgentModule<?>> alternatives = new LinkedHashMap<>();
        alternatives.put(owner, module);
        this.variants.put(id, alternatives);
        this.commands.register(id, routing.commands());
        if (profile != null) this.profiles.put(id, profile);
    }

    public synchronized void reject(Identifier id, RoutingProfile<?> profile) {
        this.checkMutable();
        if (profile.direct() || this.modules.containsKey(id) || this.profiles.containsKey(id))
            throw new IllegalArgumentException("Rejection examples require a unique non-callable ID");
        this.profiles.put(id, new RoutingProfile.Bound(id, profile.examples(), false, text -> null));
    }

    public synchronized void root(AgentAction input) {
        this.checkMutable();
        this.call(input);
        if (!this.readOnly(input.id())) throw new IllegalArgumentException("Root command must be read-only");
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

    public AgentModule<?> get(Identifier id, Predicate<Identifier> available) {
        Map.Entry<Identifier, AgentModule<?>> variant = this.variant(id, available);
        return variant == null ? null : variant.getValue();
    }

    private Map.Entry<Identifier, AgentModule<?>> variant(Identifier id, Predicate<Identifier> available) {
        Map<Identifier, AgentModule<?>> alternatives = this.variants.get(id);
        if (alternatives == null) return null;
        return alternatives.entrySet().stream().filter(entry -> entry.getKey() == null || available.test(entry.getKey()))
                .findFirst().orElse(null);
    }

    public boolean readOnly(Identifier id) {
        Entry<?> entry = this.modules.get(id);
        return entry != null && entry.readOnly();
    }

    public boolean modelSelectable(Identifier id) {
        Entry<?> entry = this.modules.get(id);
        return entry != null && entry.module().descriptor().interactive() && entry.routing().modelSelectable();
    }

    public boolean semanticSelectable(Identifier id) {
        Entry<?> entry = this.modules.get(id);
        return entry != null && entry.readOnly() && entry.routing().semanticSelectable();
    }

    public CommandRegistry commands() {
        return this.commands;
    }

    public AgentAction explicit(String text, Predicate<Identifier> available) {
        for (Entry<?> entry : this.modules.values()) {
            if (!available.test(entry.module().descriptor().id())) continue;
            AgentAction input = explicit(entry, text);
            if (input != null) return input;
        }
        return null;
    }

    private static <I extends AgentAction> I explicit(Entry<I> entry, String text) {
        I input = entry.routing().explicit().map(parser -> parser.apply(text)).orElse(null);
        return input == null ? null : AgentAction.validate(entry.module().inputCodec(), input);
    }

    public Collection<AgentModule.Descriptor> descriptors() {
        return this.modules.values().stream().map(entry -> entry.module().descriptor()).toList();
    }

    public Collection<RoutingProfile.Bound> profiles() {
        return List.copyOf(this.profiles.values());
    }

    public List<AgentResult.Action> shortcuts() {
        return this.shortcuts(_ -> true);
    }

    public List<AgentResult.Action> shortcuts(Predicate<Identifier> available) {
        return this.modules.entrySet().stream().filter(entry -> available.test(entry.getKey()))
                .map(entry -> entry.getValue().shortcut()).filter(Objects::nonNull).toList();
    }

    public Map<String, ModelRoute.Capability> capabilities() {
        return this.capabilities(_ -> true);
    }

    public Map<String, ModelRoute.Capability> capabilities(Predicate<Identifier> available) {
        Map<String, ModelRoute.Capability> capabilities = new LinkedHashMap<>();
        this.modules.forEach((id, entry) -> {
            if (this.modelSelectable(id) && available.test(id)) {
                AgentModule.Descriptor descriptor = entry.module().descriptor();
                capabilities.put(id.toString(), new ModelRoute.Capability(descriptor.description(), descriptor.schema(),
                        entry.routing().selection() == ModuleRouting.Selection.EXPLICIT_ONLY));
            }
        });
        return Collections.unmodifiableMap(capabilities);
    }

    public Codec<ModelRoute> modelCodec() {
        return this.modelCodec(_ -> true);
    }

    public Codec<ModelRoute> modelCodec(Predicate<Identifier> available) {
        List<MapCodec<? extends AgentAction>> capabilities = new ArrayList<>();
        for (Entry<?> entry : this.modules.values())
            if (this.modelSelectable(entry.module().descriptor().id()) && available.test(entry.module().descriptor().id()))
                capabilities.add(entry.module().inputCodec());
        return ModelRoute.codec(capabilities);
    }

    public <I extends AgentAction> Call<I> call(I input) {
        return this.call(input, _ -> true);
    }

    public <I extends AgentAction> Call<I> call(I input, Predicate<Identifier> available) {
        Map.Entry<Identifier, AgentModule<?>> variant = this.variant(input.id(), available);
        if (variant == null) throw new AgentException("CAPABILITY_UNAVAILABLE");
        AgentModule<?> module = variant.getValue();
        if (module.inputCodec() != input.codec()) throw new AgentException("INVALID_INPUT");
        // Registration ties this exact codec to I; keep the erased cast at this one boundary.
        @SuppressWarnings("unchecked") AgentModule<I> typed = (AgentModule<I>) module;
        return new Call<>(variant.getKey(), typed, input);
    }

    public AgentAction button(Identifier id, Map<String, String> arguments) {
        Entry<?> entry = this.modules.get(id);
        if (entry == null || !entry.module().descriptor().interactive())
            throw new AgentException("CAPABILITY_UNAVAILABLE");
        return this.button(entry, new ActionArguments(arguments));
    }

    private <I extends AgentAction> I button(Entry<I> entry, ActionArguments arguments) {
        try {
            I input = entry.routing().buttonDecoder().apply(arguments);
            return AgentAction.validate(entry.module().inputCodec(), input);
        } catch (IllegalArgumentException | IllegalStateException error) {
            throw new AgentException("INVALID_INPUT", error);
        }
    }

    private record Entry<I extends AgentAction>(AgentModule<I> module, ModuleRouting<I> routing,
                                                boolean readOnly,
                                                AgentResult.Action shortcut) {
    }

    public record Call<I extends AgentAction>(Identifier owner, AgentModule<I> module, I input) {
        public AgentResult execute(ExecutionContext context) throws Exception {
            return this.module.execute(context, this.input);
        }
    }
}
