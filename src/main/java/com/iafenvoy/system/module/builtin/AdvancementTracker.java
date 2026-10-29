package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.agent.AgentAction;
import com.iafenvoy.system.agent.AgentEngine;
import com.iafenvoy.system.agent.builtin.input.AdvancementInput;
import com.iafenvoy.system.agent.store.AgentStore;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.config.AdvancementRule;
import com.iafenvoy.system.config.file.AdvancementConfig;
import com.iafenvoy.system.module.ModuleData;
import com.iafenvoy.system.module.PlayerModule;
import com.iafenvoy.system.network.ServerNetworkHandler;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Owns advancement baselines, events buffered during login, and durable workflow dispatch.
 */
final class AdvancementTracker implements PlayerModule {
    private final MinecraftServer server;
    private final AgentEngine engine;
    private final Map<UUID, TrackingSession> sessions = new HashMap<>();

    AdvancementTracker(MinecraftServer server, AgentEngine engine) {
        this.server = server;
        this.engine = engine;
    }

    @Override
    public void validate() {
        for (AdvancementRule rule : AdvancementConfig.INSTANCE.get().rules()) {
            if (!this.engine.modules().call(rule.call()).module().descriptor().event())
                throw new IllegalArgumentException("Unknown event module " + rule.moduleId());
            if (rule.call() instanceof AdvancementInput input && (input.advancementId().isPresent() || input.ruleId().isPresent()))
                throw new IllegalArgumentException("Event metadata is supplied by the server");
        }
    }

    @Override
    public Consumer<PlayerData> binding(ServerPlayer player) {
        Map<String, Set<String>> baselines = new LinkedHashMap<>();
        for (AdvancementRule rule : AdvancementConfig.INSTANCE.get().rules())
            baselines.put(rule.id() + "|" + rule.advancementId(), this.completed(player, rule) ? Set.of(rule.advancementId().toString()) : Set.of());
        return data -> ModuleData.get(data, AdvancementSystemModule.STORAGE).baselines.putAll(baselines);
    }

    private boolean completed(ServerPlayer player, AdvancementRule rule) {
        AdvancementHolder advancement = this.server.getAdvancements().get(rule.advancementId());
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    @Override
    public Session login(ServerPlayer player) {
        TrackingSession session = new TrackingSession(player);
        this.sessions.put(player.getUUID(), session);
        return session;
    }

    @Override
    public void advancement(ServerPlayer player, Identifier advancement) {
        TrackingSession session = this.sessions.get(player.getUUID());
        if (session == null) return;
        if (!session.ready) {
            session.delayed.add(advancement);
            return;
        }
        for (AdvancementRule rule : AdvancementConfig.INSTANCE.get().rules())
            if (rule.advancementId().equals(advancement) && session.rules.stream().anyMatch(initial -> initial.id().equals(rule.id())))
                this.dispatch(player.getUUID(), rule);
    }

    private void dispatch(UUID player, AdvancementRule rule) {
        if (!this.engine.available(player, rule.call().id())) return;
        long incarnation = this.engine.incarnation(player);
        AgentStore store = this.engine.playerStore(player);
        this.engine.maintenance(() -> store.read(player, data ->
                        ModuleData.get(data, AdvancementSystemModule.STORAGE).baselines.getOrDefault(rule.id() + "|" + rule.advancementId(), Set.of()).contains(rule.advancementId().toString())))
                .thenAccept(baseline -> {
                    if (baseline || !this.engine.current(player, incarnation)) return;
                    AgentAction input = rule.call();
                    if (input instanceof AdvancementInput value)
                        input = new AdvancementInput(value.featureId(), value.rewardId(),
                                Optional.of(rule.advancementId().toString()), Optional.of(rule.id().toString()));
                    this.engine.trigger(player, rule.id() + "|" + rule.advancementId(), rule.id().toString(), input).thenAccept(result -> {
                        if (result != null && result.isSuccess()) this.server.execute(() -> {
                            ServerPlayer online = this.server.getPlayerList().getPlayer(player);
                            if (online != null && this.engine.current(player, incarnation))
                                ServerNetworkHandler.notice(online, result);
                        });
                    });
                });
    }

    private final class TrackingSession implements Session {
        private final UUID player;
        private final List<AdvancementRule> rules = AdvancementConfig.INSTANCE.get().rules();
        private final Map<Identifier, Boolean> completed = new HashMap<>();
        private final Set<Identifier> delayed = new HashSet<>();
        private final Set<Identifier> fresh = new HashSet<>();
        private boolean ready;

        private TrackingSession(ServerPlayer player) {
            this.player = player.getUUID();
            for (AdvancementRule rule : this.rules)
                this.completed.put(rule.id(), AdvancementTracker.this.completed(player, rule));
        }

        @Override
        public void initialize(PlayerData data) {
            for (AdvancementRule rule : this.rules) {
                String baseline = rule.id() + "|" + rule.advancementId();
                if (ModuleData.get(data, AdvancementSystemModule.STORAGE).baselines.containsKey(baseline)) continue;
                this.fresh.add(rule.id());
                ModuleData.get(data, AdvancementSystemModule.STORAGE).baselines.put(baseline, this.completed.get(rule.id()) ? Set.of(rule.advancementId().toString()) : Set.of());
            }
        }

        @Override
        public void ready() {
            this.ready = true;
            AdvancementTracker.this.engine.enableRules(this.player, this.rules.stream().map(rule -> rule.id().toString()).collect(Collectors.toSet()));
            for (AdvancementRule rule : this.rules)
                if ((!this.fresh.contains(rule.id()) && this.completed.get(rule.id())) || this.delayed.contains(rule.advancementId()))
                    AdvancementTracker.this.dispatch(this.player, rule);
            this.delayed.clear();
        }

        @Override
        public void close() {
            if (AdvancementTracker.this.sessions.remove(this.player, this))
                AdvancementTracker.this.engine.disableRules(this.player);
        }
    }
}
