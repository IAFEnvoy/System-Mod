package com.iafenvoy.system.module;

import com.iafenvoy.saturn.data.storage.DataStorageContext;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.execution.AgentEngine;
import com.iafenvoy.system.agent.module.AgentModule;
import com.iafenvoy.system.agent.module.ModuleScope;
import com.iafenvoy.system.agent.storage.AgentStore;
import com.iafenvoy.system.data.module.SystemModule;
import com.iafenvoy.system.data.store.PlayerData;
import com.iafenvoy.system.event.ModuleContextEvent;
import com.iafenvoy.system.module.merge.MergeContext;
import com.iafenvoy.system.module.merge.MergeRuntime;
import com.iafenvoy.system.module.merge.MergeType;
import com.iafenvoy.system.network.ServerNetworkHandler;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;

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
    private final Map<Identifier, SystemModule> installed = new LinkedHashMap<>();
    private final Map<ModuleScope, Lifecycle> lifecycles = new LinkedHashMap<>();
    private final List<PlayerModule> runtimes = new ArrayList<>();
    private final Map<UUID, PlayerSession> players = new HashMap<>();
    private final Map<UUID, Set<Identifier>> active = new ConcurrentHashMap<>();
    private final Set<UUID> flushing = ConcurrentHashMap.newKeySet();
    private final Set<UUID> flushPending = ConcurrentHashMap.newKeySet();
    private Consumer<ServerPlayer> changed = _ -> {
    };
    private boolean closed;

    public ModuleManager(MinecraftServer server, ModuleContext context) {
        this.server = server;
        this.engine = context.engine();
        try {
            NeoForge.EVENT_BUS.post(new ModuleContextEvent(context));
            Map<Identifier, SystemModule> modules = new LinkedHashMap<>();
            context.modules().keySet().stream().sorted().forEach(id -> {
                SystemModule definition = context.modules().getValue(id);
                if (definition != null && definition.available()) modules.put(id, definition);
            });
            Map<Identifier, ModuleContext> contexts = new LinkedHashMap<>();
            modules.keySet().forEach(id -> contexts.put(id, context.forModule(id)));
            Map<Identifier, GroupBuilder<?>> groups = new LinkedHashMap<>();
            modules.forEach((id, definition) -> definition.server().ifPresent(module -> {
                if (module instanceof MergeableModule<?> mergeable)
                    this.contribute(context, contexts.get(id), mergeable, groups);
            }));
            List<InstalledGroup> merged = new ArrayList<>();
            // All contributions exist before any group factory or shared workflow runs.
            groups.values().forEach(group -> {
                InstalledGroup value = group.create(context.forGroup());
                context.sharedRuntimes().forEach(this::remember);
                this.remember(value.runtime);
                this.lifecycles.put(value.scope, new Lifecycle(value.scope, value.runtime,
                        List.copyOf(value.runtime.storages()), null));
                merged.add(value);
            });
            // Tool modules export operations before workflows resolve their typed dependencies.
            modules.forEach((id, definition) -> definition.server().ifPresent(module -> {
                if (!(module instanceof ToolModule tools)) return;
                if (module instanceof WorkflowModule)
                    throw new IllegalArgumentException("Tool modules cannot expose workflows");
                this.engine.tools().registerAll(id, tools.tools(contexts.get(id)));
            }));
            merged.forEach(group -> this.engine.tools().registerExported(group.scope, group.runtime.tools()));
            // Workflows may resolve shared services before their lifecycle hooks are installed.
            modules.forEach((id, definition) -> definition.server().ifPresent(module -> {
                if (module instanceof CatalogModule<?>)
                    context.get(ContextTypes.CATALOGS).register(context.modules().get(id).orElseThrow());
                if (module instanceof WorkflowModule workflows)
                    for (AgentModule<?> workflow : workflows.workflows(contexts.get(id)))
                        this.engine.registerModule(id, workflow);
            }));
            merged.forEach(group -> {
                for (AgentModule<?> workflow : group.runtime.workflows())
                    this.engine.registerGroup(group.scope, workflow);
            });
            Set<ServerModule> initialized = Collections.newSetFromMap(new IdentityHashMap<>());
            modules.forEach((id, definition) -> {
                definition.server().filter(initialized::add).ifPresent(module -> module.installed(contexts.get(id)));
                PlayerModule runtime = definition.server().map(module -> module.runtime(contexts.get(id))).orElseGet(() -> new PlayerModule() {
                });
                context.sharedRuntimes().forEach(this::remember);
                this.remember(runtime);
                List<ModuleStorage<?>> storages = definition.server().map(ServerModule::storages).orElse(List.of());
                ModuleScope.Group group = merged.stream().map(InstalledGroup::scope)
                        .filter(scope -> scope.members().contains(id)).findFirst().orElse(null);
                Lifecycle lifecycle = new Lifecycle(new ModuleScope.Definition(id), runtime, List.copyOf(storages), group);
                this.installed.put(id, definition);
                this.lifecycles.put(lifecycle.scope, lifecycle);
            });
            context.sharedRuntimes().forEach(this::remember);
            this.access = new ModuleAccess(server, this.engine, List.copyOf(this.installed.keySet()));
            this.access.onChange(player -> {
                PlayerSession session = this.players.get(player.getUUID());
                if (session != null && session.loaded) this.reconcile(session);
            });
        } catch (RuntimeException | LinkageError error) {
            // Include shared services created by a workflow even if installation failed before runtime().
            context.sharedRuntimes().forEach(this::remember);
            this.closeRuntimes();
            throw error;
        }
    }

    private <C> void contribute(ModuleContext root, ModuleContext member, MergeableModule<C> module,
                                Map<Identifier, GroupBuilder<?>> groups) {
        MergeType<C> type = Objects.requireNonNull(module.mergeType());
        GroupBuilder<?> existing = groups.computeIfAbsent(type.id(), _ -> new GroupBuilder<>(type));
        if (existing.type != type) throw new IllegalArgumentException("Conflicting merge type " + type.id());
        // The canonical type key owns both C and the contribution class; equal IDs alone never authorize a cast.
        @SuppressWarnings("unchecked") GroupBuilder<C> group = (GroupBuilder<C>) existing;
        C contribution = type.contributionType().cast(Objects.requireNonNull(module.contribution(member)));
        group.members.add(new MergeContext.Member<>(root.modules().get(member.id()).orElseThrow(), contribution));
    }

    private void remember(PlayerModule runtime) {
        if (this.runtimes.stream().noneMatch(existing -> existing == runtime)) this.runtimes.add(runtime);
    }

    public <T extends PlayerModule> Optional<T> runtime(Class<T> type) {
        return this.closed ? Optional.empty() : this.runtimes.stream().filter(type::isInstance).map(type::cast).findFirst();
    }

    public void validate() {
        this.runtimes.forEach(PlayerModule::validate);
    }

    public void onChange(Consumer<ServerPlayer> changed) {
        this.changed = Objects.requireNonNull(changed);
    }

    public List<Identifier> enabled(UUID player) {
        return this.active.getOrDefault(player, Set.of()).stream().sorted().toList();
    }

    public boolean hasSystem(UUID player) {
        return this.access.hasSystem(player);
    }

    public Optional<Identifier> system(UUID player) {
        return this.access.system(player);
    }

    public boolean hasModule(UUID player, Identifier module) {
        if (this.server.isSameThread() && this.access.resolving(player)) return this.access.hasModule(player, module);
        return this.active.getOrDefault(player, Set.of()).contains(module);
    }

    public boolean hasType(UUID player, MapCodec<? extends ServerModule> type) {
        return this.enabled(player).stream().map(this.installed::get).filter(Objects::nonNull)
                .anyMatch(module -> module.server().filter(server -> server.codec() == type).isPresent());
    }

    public boolean add(ServerPlayer player, Identifier module) {
        boolean changed = this.access.add(player, module);
        if (changed) this.refresh(player);
        return changed;
    }

    public boolean remove(ServerPlayer player, Identifier module) {
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
        List<Consumer<PlayerData>> initializers = this.lifecycles.values().stream()
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
        Set<Identifier> definitions = Set.copyOf(this.access.enabled(player.player.getUUID()));
        Set<ModuleScope> desired = new LinkedHashSet<>();
        this.lifecycles.keySet().stream().filter(scope -> scope.available(definitions::contains)).forEach(desired::add);
        player.failed.retainAll(desired);
        desired.removeAll(player.failed);
        desired.removeIf(scope -> scope instanceof ModuleScope.Definition && this.lifecycles.get(scope).group != null
                && !desired.contains(this.lifecycles.get(scope).group));
        // Failed members cannot keep a group session alive on their own.
        desired.removeIf(scope -> scope instanceof ModuleScope.Group group
                && group.members().stream().noneMatch(id -> desired.contains(new ModuleScope.Definition(id))));
        List<ModuleScope> reverse = new ArrayList<>(player.modules.keySet());
        Collections.reverse(reverse);
        reverse.stream().filter(scope -> !desired.contains(scope)).forEach(scope -> this.stop(player, scope));
        this.publish(player);
        List<Activation> additions = new ArrayList<>();
        try {
            for (Lifecycle module : this.lifecycles.values()) {
                ModuleScope scope = module.scope;
                if (!desired.contains(scope) || player.modules.containsKey(scope)) continue;
                PlayerModule.Session session;
                try {
                    session = Objects.requireNonNull(module.runtime.login(player.player));
                } catch (RuntimeException error) {
                    player.failed.add(scope);
                    throw error;
                }
                Activation activation = new Activation(module, session);
                player.modules.put(scope, activation);
                additions.add(activation);
            }
        } catch (RuntimeException error) {
            additions.reversed().forEach(activation -> this.fail(player, activation));
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
        // Reconciliation can add a member while its group is still initializing. Serialize the data work,
        // so the group's initialize transaction always precedes its later members' transactions.
        CompletableFuture<Void> initializing = player.initializing.handle((_, _) -> (Void) null).thenCompose(_ ->
                this.engine.maintenance(() -> store.update(uuid, data -> {
                    if (!data.bound()) throw new IllegalStateException("Player system is no longer bound");
                    for (Activation activation : additions) {
                        if (!activation.valid) continue;
                        activation.module.storages.forEach(storage -> ModuleData.initialize(data, storage, gameTime));
                    }
                    for (Activation activation : additions)
                        if (activation.valid) activation.session.initialize(data);
                    return (Void) null;
                }))).toCompletableFuture();
        player.initializing = initializing;
        initializing.whenComplete((_, error) -> this.server.execute(() -> {
            if (!this.current(player)) return;
            for (Activation activation : additions) {
                if (player.modules.get(activation.module.scope) != activation) continue;
                if (error != null) this.fail(player, activation);
                else activation.initialized = true;
            }
            if (error != null) {
                player.initialized.completeExceptionally(error);
                SystemMod.LOGGER.warn("Unable to initialize player module data for {}", uuid, error);
            }
            this.startReady(player);
            this.reconcile(player);
            this.engine.resume(uuid);
            this.finishInitialization(player);
        }));
    }

    private void startReady(PlayerSession player) {
        // Lifecycles are ordered group-before-member, including when different batches finish together.
        for (Activation activation : player.modules.values()) {
            ModuleScope.Group group = activation.module.group;
            Activation dependency = group == null ? null : player.modules.get(group);
            if (activation.initialized && (group == null || dependency != null && dependency.ready))
                activation.ready = true;
        }
        this.publish(player);
        for (Activation activation : List.copyOf(player.modules.values())) {
            if (!activation.ready || activation.started || player.modules.get(activation.module.scope) != activation)
                continue;
            try {
                activation.started = true;
                activation.session.ready();
            } catch (RuntimeException failure) {
                this.fail(player, activation);
                player.initialized.completeExceptionally(failure);
                SystemMod.LOGGER.warn("Unable to start module {} for {}", activation.module.scope, player.player.getUUID(), failure);
            }
        }
        this.publish(player);
    }

    private void finishInitialization(PlayerSession player) {
        if (player.modules.values().stream().allMatch(module -> module.ready)) player.initialized.complete(true);
    }

    private void publish(PlayerSession player) {
        Set<Identifier> next = new LinkedHashSet<>();
        player.modules.forEach((scope, activation) -> {
            Activation group = activation.module.group == null ? null : player.modules.get(activation.module.group);
            if (activation.ready && (activation.module.group == null || group != null && group.ready)
                    && scope instanceof ModuleScope.Definition(Identifier id)) next.add(id);
        });
        Set<Identifier> previous = this.active.put(player.player.getUUID(), Set.copyOf(next));
        if (!next.equals(previous)) {
            this.engine.moduleAccessChanged(player.player.getUUID());
            this.changed.accept(player.player);
        }
    }

    private void fail(PlayerSession player, Activation activation) {
        if (player.modules.get(activation.module.scope) != activation) return;
        player.failed.add(activation.module.scope);
        this.stop(player, activation.module.scope);
    }

    private void stop(PlayerSession player, ModuleScope scope) {
        Activation activation = player.modules.remove(scope);
        if (activation == null) return;
        activation.valid = false;
        // Revoke worker access before close hooks run or dispatch any further work.
        if (this.players.get(player.player.getUUID()) == player) this.publish(player);
        if (scope instanceof ModuleScope.Group group)
            group.members().forEach(id -> this.stop(player, new ModuleScope.Definition(id)));
        try {
            activation.session.close();
        } catch (RuntimeException error) {
            SystemMod.LOGGER.warn("Unable to close module {} for {}", scope, player.player.getUUID(), error);
        }
        ModuleScope.Group group = activation.module.group;
        if (group != null && group.members().stream().noneMatch(id -> player.modules.containsKey(new ModuleScope.Definition(id))))
            this.stop(player, group);
    }

    public void tick() {
        this.checkThread();
        if (this.closed) return;
        this.access.tick();
        for (PlayerModule runtime : this.runtimes) {
            try {
                runtime.tick();
            } catch (RuntimeException error) {
                SystemMod.LOGGER.warn("Module maintenance failed for {}", runtime.getClass().getSimpleName(), error);
            }
        }
        for (PlayerSession player : List.copyOf(this.players.values())) {
            if (!this.current(player)) continue;
            List<Activation> ticking = player.modules.values().stream()
                    .filter(activation -> activation.ready && activation.module.storages.stream().anyMatch(ModuleStorage::ticking)).toList();
            if (!ticking.isEmpty()) {
                DataStorageContext context = new DataStorageContext(player.player, FormulaContext.of(player.player), player.player.level().getGameTime());
                UUID uuid = player.player.getUUID();
                AgentStore store = this.engine.playerStore(uuid);
                try {
                    store.updateLoaded(uuid, data -> {
                        boolean dirty = false;
                        Set<Identifier> owners = new LinkedHashSet<>();
                        ticking.forEach(activation -> activation.module.storages.forEach(storage -> owners.add(storage.owner())));
                        for (Identifier owner : owners) {
                            data.moduleData.tick(owner, context);
                            dirty |= data.moduleData.isDirty(owner);
                        }
                        return dirty;
                    });
                } catch (RuntimeException error) {
                    ticking.forEach(activation -> this.fail(player, activation));
                    this.publish(player);
                    SystemMod.LOGGER.warn("Module storage tick failed for {}", player.player.getUUID(), error);
                }
                // Flush off-thread at most once per second, coalescing writes for this player.
                if (context.gameTime() % 20 == 0) this.flush(player.player.getUUID());
            }
            for (Activation activation : List.copyOf(player.modules.values())) {
                if (!activation.ready || player.modules.get(activation.module.scope) != activation) continue;
                try {
                    activation.session.tick();
                } catch (RuntimeException error) {
                    this.fail(player, activation);
                    this.publish(player);
                    SystemMod.LOGGER.warn("Module tick failed for {} / {}", player.player.getUUID(), activation.module.scope, error);
                }
            }
        }
    }

    public void advancement(ServerPlayer player, Identifier advancement) {
        this.checkThread();
        PlayerSession session = this.players.get(player.getUUID());
        if (session == null || !this.current(session)) return;
        for (Activation activation : List.copyOf(session.modules.values())) {
            if (!activation.ready || session.modules.get(activation.module.scope) != activation) continue;
            try {
                activation.module.runtime.advancement(player, advancement);
            } catch (RuntimeException error) {
                SystemMod.LOGGER.warn("Module advancement hook failed for {} / {}", player.getUUID(), activation.module.scope, error);
            }
        }
    }

    public void logout(UUID player) {
        this.checkThread();
        PlayerSession session = this.players.remove(player);
        this.active.remove(player);
        if (session == null) return;
        this.engine.moduleAccessChanged(player);
        List<ModuleScope> reverse = new ArrayList<>(session.modules.keySet());
        Collections.reverse(reverse);
        reverse.forEach(id -> this.stop(session, id));
        session.access.close();
        this.flush(player);
        session.initialized.complete(false);
    }

    /**
     * Coalesce memory-only module mutations into worker-side persistence without blocking the game thread.
     */
    public void flush(UUID player) {
        if (!this.flushing.add(player)) {
            // Never drop a later request: an in-flight flush may have read the cache before these changes.
            this.flushPending.add(player);
            return;
        }
        long incarnation = this.engine.incarnation(player);
        AgentStore store = this.engine.playerStore(player);
        this.engine.maintenance(() -> {
            store.flush(player);
            return null;
        }).whenComplete((_, error) -> {
            this.flushing.remove(player);
            if (error != null && this.engine.current(player, incarnation))
                SystemMod.LOGGER.warn("Unable to flush player module data for {}", player, error);
            // The closed manager has already handed pending data to the store; the store flushes on close.
            if (!this.closed && this.flushPending.remove(player)) this.flush(player);
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
        try {
            List.copyOf(this.players.keySet()).forEach(this::logout);
        } finally {
            this.closeRuntimes();
        }
    }

    private void closeRuntimes() {
        List<PlayerModule> reverse = new ArrayList<>(this.runtimes);
        Collections.reverse(reverse);
        for (PlayerModule runtime : reverse) {
            try {
                runtime.close();
            } catch (RuntimeException | LinkageError error) {
                SystemMod.LOGGER.warn("Unable to close module runtime {}", runtime.getClass().getSimpleName(), error);
            }
        }
        this.runtimes.clear();
    }

    private record Lifecycle(ModuleScope scope, PlayerModule runtime, List<ModuleStorage<?>> storages,
                             ModuleScope.Group group) {
    }

    private record InstalledGroup(ModuleScope.Group scope, MergeRuntime runtime) {
    }

    private static final class GroupBuilder<C> {
        private final MergeType<C> type;
        private final List<MergeContext.Member<C>> members = new ArrayList<>();

        private GroupBuilder(MergeType<C> type) {
            this.type = type;
        }

        private InstalledGroup create(ModuleContext services) {
            MergeContext<C> context = new MergeContext<>(services, this.type, this.members);
            return new InstalledGroup(context.scope(), Objects.requireNonNull(this.type.create(context)));
        }
    }

    private static final class Activation {
        private final Lifecycle module;
        private final PlayerModule.Session session;
        private boolean initialized;
        private boolean ready;
        private boolean started;
        private volatile boolean valid = true;

        private Activation(Lifecycle module, PlayerModule.Session session) {
            this.module = module;
            this.session = session;
        }
    }

    private final class PlayerSession {
        private final ServerPlayer player;
        private final UUID connection;
        private final long incarnation;
        private final PlayerModule.Session access;
        private final Map<ModuleScope, Activation> modules = new LinkedHashMap<>();
        private final Set<ModuleScope> failed = new HashSet<>();
        private final CompletableFuture<Boolean> initialized = new CompletableFuture<>();
        private CompletableFuture<Void> initializing = CompletableFuture.completedFuture(null);
        private boolean loaded;

        private PlayerSession(ServerPlayer player, PlayerModule.Session access) {
            this.player = player;
            this.connection = ServerNetworkHandler.connectionId(player);
            this.incarnation = ModuleManager.this.engine.incarnation(player.getUUID());
            this.access = access;
        }
    }
}
