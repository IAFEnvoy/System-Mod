package com.iafenvoy.system.module;

import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentEngine;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.store.AgentStore;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.data.ModuleDefinition;
import com.iafenvoy.system.module.core.CoreSystemModule;
import com.iafenvoy.system.registry.SystemDataPackRegistries;
import com.iafenvoy.system.registry.SystemRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Internal entitlement resolver; ModuleManager publishes only successfully activated modules.
 */
final class ModuleAccess implements PlayerModule {
    private final MinecraftServer server;
    private final AgentEngine engine;
    private final Set<Identifier> installed, required;
    private final Map<UUID, ModuleAccessDataStorage> overrides = new HashMap<>();
    private final Map<UUID, CompletableFuture<Void>> pendingWrites = new ConcurrentHashMap<>();
    private final Map<UUID, Set<Identifier>> enabled = new ConcurrentHashMap<>();
    private final Map<UUID, Identifier> systems = new ConcurrentHashMap<>();
    private final Map<UUID, ServerPlayer> players = new HashMap<>();
    private final Set<String> reportedErrors = new HashSet<>();
    private Resolution resolving;
    private Consumer<ServerPlayer> changed = _ -> {
    };
    private int ticks;

    public ModuleAccess(MinecraftServer server, AgentEngine engine, List<Identifier> installed) {
        this.server = server;
        this.engine = engine;
        this.installed = Set.copyOf(installed);
        this.required = SystemRegistries.SERVER_MODULE.stream().filter(module -> this.installed.contains(module.id()))
                .filter(ServerModule::required).map(ServerModule::id)
                .collect(Collectors.toUnmodifiableSet());
    }

    public void onChange(Consumer<ServerPlayer> changed) {
        this.changed = changed;
    }

    public List<Identifier> enabled(UUID player) {
        return this.enabled.getOrDefault(player, Set.of()).stream().sorted().toList();
    }

    public boolean hasSystem(UUID player) {
        return this.enabled.containsKey(player) && this.engine.current(player, this.engine.incarnation(player));
    }

    public Optional<Identifier> system(UUID player) {
        return this.hasSystem(player) ? Optional.ofNullable(this.systems.get(player)) : Optional.empty();
    }

    public boolean hasModule(UUID player, Identifier module) {
        Resolution resolution = this.server.isSameThread() ? this.resolving : null;
        if (resolution != null && resolution.player.getUUID().equals(player)) return resolution.test(module);
        return this.enabled.getOrDefault(player, Set.of()).contains(module);
    }

    boolean resolving(UUID player) {
        return this.resolving != null && this.resolving.player.getUUID().equals(player);
    }

    // Only ModuleManager changes entitlements, so player sessions follow the same lifecycle.
    public boolean add(ServerPlayer player, Identifier module) {
        return this.set(player, module, true);
    }

    public boolean remove(ServerPlayer player, Identifier module) {
        return this.set(player, module, false);
    }

    private boolean set(ServerPlayer player, Identifier module, boolean value) {
        if (!this.server.isSameThread()) throw new IllegalStateException("Module overrides require the server thread");
        if (!this.hasSystem(player.getUUID()) || !this.installed.contains(module) || this.required.contains(module))
            return false;
        UUID uuid = player.getUUID();
        if (this.players.get(uuid) != player) return false;
        ModuleAccessDataStorage selection = this.overrides.get(uuid);
        if (selection == null) return false;
        boolean changed = selection.set(module, value);
        if (changed) {
            this.persist(uuid, selection.copy());
            this.refresh(player);
        }
        return changed;
    }

    public void clear(UUID player) {
        this.overrides.remove(player);
        this.pendingWrites.remove(player);
    }

    public void refresh(ServerPlayer player) {
        if (this.players.get(player.getUUID()) != player) return;
        Resolution resolution = new Resolution(player);
        this.resolving = resolution;
        Set<Identifier> next;
        try {
            Set<Identifier> result = new LinkedHashSet<>();
            this.installed.stream().sorted().filter(resolution::test).forEach(result::add);
            next = Set.copyOf(result);
        } finally {
            this.resolving = null;
        }
        Set<Identifier> previous = this.enabled.put(player.getUUID(), next);
        if (!next.equals(previous)) {
            this.changed.accept(player);
        }
    }

    @Override
    public void tick() {
        if (++this.ticks % 20 == 0) List.copyOf(this.players.values()).forEach(this::refresh);
    }

    @Override
    public Session login(ServerPlayer player) {
        AtomicReference<ModuleAccessDataStorage> saved = new AtomicReference<>();
        AtomicReference<Identifier> system = new AtomicReference<>();
        return new Session() {
            @Override
            public void initialize(PlayerData data) {
                system.set(data.systems.current().orElseThrow(() -> new AgentException("NOT_BOUND")));
                saved.set(ModuleData.get(data, CoreSystemModule.ACCESS).copy());
            }

            @Override
            public void ready() {
                // A removed definition never falls back to a different system or grants its modules.
                if (SystemDataPackRegistries.get(ModuleAccess.this.server.registryAccess(), system.get()).isEmpty())
                    throw new AgentException("SYSTEM_UNAVAILABLE");
                ModuleAccess.this.players.put(player.getUUID(), player);
                ModuleAccess.this.systems.put(player.getUUID(), system.get());
                ModuleAccess.this.overrides.putIfAbsent(player.getUUID(), saved.get());
                ModuleAccess.this.enabled.put(player.getUUID(), Set.of());
                ModuleAccess.this.refresh(player);
            }

            @Override
            public void close() {
                if (ModuleAccess.this.players.remove(player.getUUID(), player)) {
                    ModuleAccess.this.enabled.remove(player.getUUID());
                    ModuleAccess.this.systems.remove(player.getUUID());
                }
            }
        };
    }

    private void persist(UUID player, ModuleAccessDataStorage snapshot) {
        AgentStore store = this.engine.playerStore(player);
        CompletableFuture<Void> previous = this.pendingWrites.getOrDefault(player, CompletableFuture.completedFuture(null));
        CompletableFuture<Void> next = previous.handle((_, error) -> null).thenCompose(_ ->
                this.engine.<Void>maintenance(() -> {
                    store.update(player, data -> {
                        ModuleData.get(data, CoreSystemModule.ACCESS).replace(snapshot.selection());
                        return null;
                    });
                    return null;
                })).toCompletableFuture();
        this.pendingWrites.put(player, next);
        next.whenComplete((_, error) -> {
            if (error != null) SystemMod.LOGGER.warn("Unable to save module access for {}", player, error);
            this.pendingWrites.remove(player, next);
        });
    }

    private final class Resolution {
        private final ServerPlayer player;
        private final ModuleAccessDataStorage.Selection selection;
        private final List<ModuleDefinition> definitions;
        private final Map<Identifier, Boolean> results = new HashMap<>();
        private final Set<Identifier> visiting = new LinkedHashSet<>(), cyclic = new HashSet<>();

        private Resolution(ServerPlayer player) {
            this.player = player;
            this.selection = ModuleAccess.this.overrides.getOrDefault(player.getUUID(), new ModuleAccessDataStorage()).selection();
            Identifier system = ModuleAccess.this.systems.get(player.getUUID());
            this.definitions = SystemDataPackRegistries.get(ModuleAccess.this.server.registryAccess(), system)
                    .orElseThrow(() -> new AgentException("SYSTEM_UNAVAILABLE")).modules();
        }

        private boolean test(Identifier module) {
            if (!ModuleAccess.this.installed.contains(module)) return false;
            if (ModuleAccess.this.required.contains(module)) return true;
            if (this.selection.removed().contains(module)) return false;
            if (this.selection.added().contains(module)) return true;
            if (this.results.containsKey(module)) return this.results.get(module);
            if (!this.visiting.add(module)) {
                boolean cycle = false;
                for (Identifier entry : this.visiting) {
                    cycle |= entry.equals(module);
                    if (cycle) this.cyclic.add(entry);
                }
                this.report(module, "Cyclic module condition", null);
                return false;
            }
            boolean result = false;
            try {
                EntityConditionContext context = new EntityConditionContext(this.player, FormulaContext.of(this.player));
                result = this.definitions.stream().filter(definition -> definition.matches(module))
                        .anyMatch(definition -> definition.condition().test(context));
            } catch (RuntimeException error) {
                this.report(module, "Module condition failed", error);
            } finally {
                this.visiting.remove(module);
            }
            result &= !this.cyclic.contains(module);
            this.results.put(module, result);
            return result;
        }

        private void report(Identifier module, String message, Throwable error) {
            if (ModuleAccess.this.reportedErrors.add(message + ":" + module))
                SystemMod.LOGGER.warn("{}: {}", message, module, error);
        }
    }
}
