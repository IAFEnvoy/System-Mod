package com.iafenvoy.system.module;

import com.iafenvoy.saturn.data.storage.DataStorageContext;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentEngine;
import com.iafenvoy.system.agent.AgentModule;
import com.iafenvoy.system.agent.store.AgentStore;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.data.SystemModule;
import com.iafenvoy.system.network.ServerNetworkHandler;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Owns module activation, player sessions and tick dispatch; workers see only ready module IDs.
 */
public final class ModuleManager implements AutoCloseable {
    private final MinecraftServer server;
    private final AgentEngine engine;
    private final ModuleAccess access;
    private final Map<ResourceLocation, Installed> installed = new LinkedHashMap<>();
    private final Map<UUID, PlayerSession> players = new HashMap<>();
    private final Map<UUID, Set<ResourceLocation>> active = new ConcurrentHashMap<>();
    private final Set<UUID> flushing = ConcurrentHashMap.newKeySet();
    private Consumer<ServerPlayer> changed = unused -> {
    };
    private boolean closed;

    public ModuleManager(MinecraftServer server, ModuleContext context) {
        this.server = server;
        this.engine = context.engine();
        Map<ResourceLocation, SystemModule> modules = new LinkedHashMap<>();
        context.modules().keySet().stream().sorted().forEach(id -> {
            SystemModule definition = context.modules().get(id);
            if (definition.available()) modules.put(id, definition);
        });
        Map<ResourceLocation, ModuleContext> contexts = new LinkedHashMap<>();
        modules.keySet().forEach(id -> contexts.put(id, context.forModule(id)));
        // Tool modules export operations before workflows resolve their typed dependencies.
        modules.forEach((id, definition) -> definition.server().ifPresent(module -> {
            if (!(module instanceof ToolModule tools)) return;
            if (module instanceof WorkflowModule)
                throw new IllegalArgumentException("Tool modules cannot expose workflows");
            this.engine.tools().registerAll(id, tools.tools(contexts.get(id)));
        }));
        // Configured definitions create fresh workflows before any per-server runtime is constructed.
        modules.forEach((id, definition) -> definition.server().ifPresent(module -> {
            if (module instanceof WorkflowModule workflows)
                for (AgentModule<?> workflow : workflows.workflows(contexts.get(id))) this.engine.registerModule(id, workflow);
        }));
        Set<ServerModule> initialized = Collections.newSetFromMap(new IdentityHashMap<>());
        modules.forEach((id, definition) -> {
            definition.server().filter(initialized::add).ifPresent(module -> module.installed(contexts.get(id)));
            PlayerModule runtime = definition.server().map(module -> module.runtime(contexts.get(id))).orElseGet(() -> new PlayerModule() { });
            List<ModuleStorage<?>> storages = definition.server().map(ServerModule::storages).orElse(List.of());
            this.installed.put(id, new Installed(id, definition, runtime, storages));
        });
        this.access = new ModuleAccess(server, this.engine, List.copyOf(this.installed.keySet()));
        this.access.onChange(player -> {
            PlayerSession session = this.players.get(player.getUUID());
            if (session != null && session.loaded) this.reconcile(session);
        });
    }

    public void validate() {
        this.installed.values().forEach(module -> module.runtime.validate());
    }

    public void onChange(Consumer<ServerPlayer> changed) {
        this.changed = Objects.requireNonNull(changed);
    }

    public List<ResourceLocation> enabled(UUID player) {
        return this.active.getOrDefault(player, Set.of()).stream().sorted().toList();
    }

    public boolean hasSystem(UUID player) {
        return this.access.hasSystem(player);
    }

    public Optional<ResourceLocation> system(UUID player) {
        return this.access.system(player);
    }

    public boolean hasModule(UUID player, ResourceLocation module) {
        if (this.server.isSameThread() && this.access.resolving(player)) return this.access.hasModule(player, module);
        return this.active.getOrDefault(player, Set.of()).contains(module);
    }

    public boolean hasType(UUID player, MapCodec<? extends ServerModule> type) {
        return this.enabled(player).stream().map(this.installed::get).filter(Objects::nonNull)
                .anyMatch(module -> module.definition.server().filter(server -> server.codec() == type).isPresent());
    }

    public boolean add(ServerPlayer player, ResourceLocation module) {
        boolean changed = this.access.add(player, module);
        if (changed) this.refresh(player);
        return changed;
    }

    public boolean remove(ServerPlayer player, ResourceLocation module) {
        boolean changed = this.access.remove(player, module);
        if (changed) this.refresh(player);
        return changed;
    }

    public void refresh(ServerPlayer player) {
        this.checkThread();
        this.access.refresh(player);
        PlayerSession session = this.players.get(player.getUUID());
        if (session != null && session.loaded) this.reconcile(session);
    }

    /**
     * Snapshot live game state before the binding transaction runs off the server thread.
     */
    public Consumer<PlayerData> binding(ServerPlayer player) {
        this.checkThread();
        List<Consumer<PlayerData>> initializers = this.installed.values().stream()
                .map(module -> module.runtime.binding(player)).toList();
        return data -> initializers.forEach(initialize -> initialize.accept(data));
    }

    public CompletionStage<Boolean> login(ServerPlayer player) {
        this.checkThread();
        this.logout(player.getUUID());
        PlayerSession session = new PlayerSession(player, this.access.login(player));
        this.players.put(player.getUUID(), session);
        AgentStore store = this.engine.playerStore(player.getUUID());
        this.engine.maintenance(() -> {
            if (!store.read(player.getUUID(), PlayerData::bound)) return false;
            return store.update(player.getUUID(), data -> {
                session.access.initialize(data);
                return true;
            });
        }).whenComplete((bound, error) -> this.server.execute(() -> {
            if (!this.current(session)) return;
            if (error != null || !Boolean.TRUE.equals(bound)) {
                if (error != null) session.initialized.completeExceptionally(error);
                else session.initialized.complete(false);
                this.logout(player.getUUID());
                return;
            }
            try {
                session.access.ready();
                session.loaded = true;
                this.reconcile(session);
            } catch (RuntimeException failure) {
                session.initialized.completeExceptionally(failure);
                this.logout(player.getUUID());
            }
        }));
        return session.initialized;
    }

    private void reconcile(PlayerSession player) {
        if (!this.current(player)) return;
        Set<ResourceLocation> desired = Set.copyOf(this.access.enabled(player.player.getUUID()));
        player.failed.retainAll(desired);
        for (ResourceLocation id : List.copyOf(player.modules.keySet()))
            if (!desired.contains(id)) this.stop(player, id);
        this.publish(player);
        List<Activation> additions = new ArrayList<>();
        try {
            for (Installed module : this.installed.values()) {
                ResourceLocation id = module.id();
                if (!desired.contains(id) || player.modules.containsKey(id) || player.failed.contains(id)) continue;
                PlayerModule.Session session;
                try {
                    session = Objects.requireNonNull(module.runtime.login(player.player));
                } catch (RuntimeException error) {
                    player.failed.add(id);
                    throw error;
                }
                Activation activation = new Activation(module, session);
                player.modules.put(id, activation);
                additions.add(activation);
            }
        } catch (RuntimeException error) {
            additions.forEach(activation -> this.stop(player, activation.module.id()));
            this.publish(player);
            player.initialized.completeExceptionally(error);
            SystemMod.LOGGER.warn("Unable to create player module sessions for {}", player.player.getUUID(), error);
            return;
        }
        if (additions.isEmpty()) {
            this.finishInitialization(player);
            return;
        }
        UUID uuid = player.player.getUUID();
        AgentStore store = this.engine.playerStore(uuid);
        long gameTime = player.player.level().getGameTime();
        this.engine.maintenance(() -> store.update(uuid, data -> {
            if (!data.bound()) throw new IllegalStateException("Player system is no longer bound");
            for (Activation activation : additions) {
                if (!activation.valid) continue;
                activation.module.storages.forEach(storage -> ModuleData.initialize(data, storage, gameTime));
                activation.session.initialize(data);
            }
            return null;
        })).whenComplete((unused1, error) -> this.server.execute(() -> {
            if (!this.current(player)) return;
            for (Activation activation : additions) {
                ResourceLocation id = activation.module.id();
                // A removed/re-added module owns a different activation; stale callbacks cannot restart it.
                if (player.modules.get(id) != activation) continue;
                if (error != null) {
                    this.stop(player, id);
                    player.failed.add(id);
                    continue;
                }
                activation.ready = true;
            }
            // Publish the whole initialized batch before ready hooks dispatch workflows owned by other modules.
            this.publish(player);
            if (error != null) {
                player.initialized.completeExceptionally(error);
                SystemMod.LOGGER.warn("Unable to initialize player module data for {}", uuid, error);
                return;
            }
            for (Activation activation : additions) {
                ResourceLocation id = activation.module.id();
                if (player.modules.get(id) != activation || !activation.ready) continue;
                try {
                    activation.session.ready();
                } catch (RuntimeException failure) {
                    this.stop(player, id);
                    player.failed.add(id);
                    this.publish(player);
                    player.initialized.completeExceptionally(failure);
                    SystemMod.LOGGER.warn("Unable to start module {} for {}", id, uuid, failure);
                }
            }
            this.publish(player);
            this.engine.resume(uuid);
            this.finishInitialization(player);
        }));
    }

    private void finishInitialization(PlayerSession player) {
        if (player.modules.values().stream().allMatch(module -> module.ready))
            player.initialized.complete(true);
    }

    private void publish(PlayerSession player) {
        Set<ResourceLocation> next = new LinkedHashSet<>();
        player.modules.forEach((id, activation) -> {
            if (activation.ready) next.add(id);
        });
        Set<ResourceLocation> previous = this.active.put(player.player.getUUID(), Set.copyOf(next));
        if (!next.equals(previous)) {
            this.engine.moduleAccessChanged(player.player.getUUID());
            this.changed.accept(player.player);
        }
    }

    private void stop(PlayerSession player, ResourceLocation id) {
        Activation activation = player.modules.remove(id);
        if (activation == null) return;
        activation.valid = false;
        // Revoke worker access before close hooks run or dispatch any further work.
        if (this.players.get(player.player.getUUID()) == player) this.publish(player);
        try {
            activation.session.close();
        } catch (RuntimeException error) {
            SystemMod.LOGGER.warn("Unable to close module {} for {}", id, player.player.getUUID(), error);
        }
    }

    public void tick() {
        this.checkThread();
        if (this.closed) return;
        this.access.tick();
        for (Installed module : this.installed.values()) {
            try {
                module.runtime.tick();
            } catch (RuntimeException error) {
                SystemMod.LOGGER.warn("Module maintenance failed for {}", module.id(), error);
            }
        }
        for (PlayerSession player : List.copyOf(this.players.values())) {
            if (!this.current(player)) continue;
            List<ResourceLocation> ticking = player.modules.entrySet().stream()
                    .filter(entry -> entry.getValue().ready && entry.getValue().module.storages.stream().anyMatch(ModuleStorage::ticking))
                    .map(Map.Entry::getKey).toList();
            if (!ticking.isEmpty()) {
                DataStorageContext context = new DataStorageContext(player.player, FormulaContext.of(player.player), player.player.level().getGameTime());
                UUID uuid = player.player.getUUID();
                AgentStore store = this.engine.playerStore(uuid);
                try {
                    store.updateLoaded(uuid, data -> {
                        boolean dirty = false;
                        Set<ResourceLocation> owners = new LinkedHashSet<>();
                        ticking.forEach(id -> this.installed.get(id).storages.forEach(storage -> owners.add(storage.owner())));
                        for (ResourceLocation owner : owners) {
                            data.moduleData.tick(owner, context);
                            dirty |= data.moduleData.isDirty(owner);
                        }
                        return dirty;
                    });
                } catch (RuntimeException error) {
                    ticking.forEach(id -> {
                        this.stop(player, id);
                        player.failed.add(id);
                    });
                    this.publish(player);
                    SystemMod.LOGGER.warn("Module storage tick failed for {}", player.player.getUUID(), error);
                }
                // Flush off-thread at most once per second, coalescing writes for this player.
                if (context.gameTime() % 20 == 0) this.flush(player.player.getUUID());
            }
            for (Activation activation : List.copyOf(player.modules.values())) {
                if (!activation.ready || player.modules.get(activation.module.id()) != activation) continue;
                try {
                    activation.session.tick();
                } catch (RuntimeException error) {
                    this.stop(player, activation.module.id());
                    player.failed.add(activation.module.id());
                    this.publish(player);
                    SystemMod.LOGGER.warn("Module tick failed for {} / {}", player.player.getUUID(), activation.module.id(), error);
                }
            }
        }
    }

    public void advancement(ServerPlayer player, ResourceLocation advancement) {
        this.checkThread();
        PlayerSession session = this.players.get(player.getUUID());
        if (session == null || !this.current(session)) return;
        for (Activation activation : List.copyOf(session.modules.values())) {
            if (session.modules.get(activation.module.id()) != activation) continue;
            try {
                activation.module.runtime.advancement(player, advancement);
            } catch (RuntimeException error) {
                SystemMod.LOGGER.warn("Module advancement hook failed for {} / {}", player.getUUID(), activation.module.id(), error);
            }
        }
    }

    public void logout(UUID player) {
        this.checkThread();
        PlayerSession session = this.players.remove(player);
        this.active.remove(player);
        if (session == null) return;
        this.engine.moduleAccessChanged(player);
        List<ResourceLocation> reverse = new ArrayList<>(session.modules.keySet());
        Collections.reverse(reverse);
        reverse.forEach(id -> this.stop(session, id));
        session.access.close();
        this.flush(player);
        session.initialized.complete(false);
    }

    private void flush(UUID player) {
        if (!this.flushing.add(player)) return;
        long incarnation = this.engine.incarnation(player);
        AgentStore store = this.engine.playerStore(player);
        this.engine.maintenance(() -> {
            store.flush(player);
            return null;
        }).whenComplete((unused1, error) -> {
            this.flushing.remove(player);
            if (error != null && this.engine.current(player, incarnation))
                SystemMod.LOGGER.warn("Unable to flush player module data for {}", player, error);
        });
    }

    public void clear(UUID player) {
        this.logout(player);
        this.access.clear(player);
    }

    private boolean current(PlayerSession player) {
        return !this.closed && this.players.get(player.player.getUUID()) == player
                && this.server.getPlayerList().getPlayer(player.player.getUUID()) == player.player
                && player.connection.equals(ServerNetworkHandler.connectionId(player.player))
                && this.engine.current(player.player.getUUID(), player.incarnation);
    }

    private void checkThread() {
        if (!this.server.isSameThread()) throw new IllegalStateException("Module lifecycle requires the server thread");
    }

    @Override
    public void close() {
        this.checkThread();
        if (this.closed) return;
        this.closed = true;
        List.copyOf(this.players.keySet()).forEach(this::logout);
        List<Installed> reverse = new ArrayList<>(this.installed.values());
        Collections.reverse(reverse);
        for (Installed module : reverse) {
            try {
                module.runtime.close();
            } catch (RuntimeException error) {
                SystemMod.LOGGER.warn("Unable to close module runtime {}", module.id(), error);
            }
        }
    }

    private record Installed(ResourceLocation id, SystemModule definition, PlayerModule runtime, List<ModuleStorage<?>> storages) {
    }

    private static final class Activation {
        private final Installed module;
        private final PlayerModule.Session session;
        private boolean ready;
        private volatile boolean valid = true;

        private Activation(Installed module, PlayerModule.Session session) {
            this.module = module;
            this.session = session;
        }
    }

    private final class PlayerSession {
        private final ServerPlayer player;
        private final UUID connection;
        private final long incarnation;
        private final PlayerModule.Session access;
        private final Map<ResourceLocation, Activation> modules = new LinkedHashMap<>();
        private final Set<ResourceLocation> failed = new HashSet<>();
        private final CompletableFuture<Boolean> initialized = new CompletableFuture<>();
        private boolean loaded;

        private PlayerSession(ServerPlayer player, PlayerModule.Session access) {
            this.player = player;
            this.connection = ServerNetworkHandler.connectionId(player);
            this.incarnation = ModuleManager.this.engine.incarnation(player.getUUID());
            this.access = access;
        }
    }
}
