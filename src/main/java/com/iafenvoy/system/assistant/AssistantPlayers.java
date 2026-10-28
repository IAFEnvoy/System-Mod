package com.iafenvoy.system.assistant;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentEngine;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentResponse;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.store.AgentStore;
import com.iafenvoy.system.assistant.usage.AssistantUsage;
import com.iafenvoy.system.module.PlayerModule;
import com.iafenvoy.system.network.ServerNetworkHandler;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * Per-server binding and player lifecycle; features contribute their own login and event hooks.
 */
public final class AssistantPlayers {
    private final MinecraftServer server;
    private final AgentEngine engine;
    private final List<PlayerModule> modules;
    private final AssistantUsage usage;
    private final Map<UUID, Long> lastRequest = new HashMap<>();
    private final Set<UUID> ready = new HashSet<>();
    private final Map<UUID, List<PlayerModule.Session>> sessions = new HashMap<>();
    private final Map<UUID, UUID> initializations = new HashMap<>();
    private final Set<UUID> bindingChanges = new HashSet<>();
    private final Set<UUID> pendingBindingIntroductions = new HashSet<>();

    AssistantPlayers(MinecraftServer server, AgentEngine engine, List<PlayerModule> modules, AssistantUsage usage) {
        this.server = server;
        this.engine = engine;
        this.modules = List.copyOf(modules);
        this.usage = usage;
    }

    void validateModules() {
        this.modules.forEach(PlayerModule::validate);
    }

    public void refreshPlayer(ServerPlayer player) {
        this.usage.rememberPlayer(player);
        this.engine.language(player.getUUID(), player.clientInformation().language());
    }

    boolean admitRequest(UUID player) {
        long now = System.currentTimeMillis();
        if (now - this.lastRequest.getOrDefault(player, 0L) < 350) return false;
        this.lastRequest.put(player, now);
        return true;
    }

    /**
     * Completed login sessions form the main-thread binding cache; notification paths never read files on ticks.
     */
    public boolean notificationsEnabled(ServerPlayer player) {
        UUID uuid = player.getUUID();
        return this.server.getPlayerList().getPlayer(uuid) == player && this.ready.contains(uuid)
                && !this.initializations.containsKey(uuid) && !this.bindingChanges.contains(uuid)
                && this.engine.current(uuid, this.engine.incarnation(uuid));
    }

    public void notifyEvent(ServerPlayer player, AgentResult result) {
        if (this.notificationsEnabled(player)) ServerNetworkHandler.notice(player, result);
    }

    // Only a new successful binding schedules onboarding; login merely restores notification readiness.
    public boolean consumeBindingIntroduction(ServerPlayer player) {
        return this.notificationsEnabled(player) && this.pendingBindingIntroductions.remove(player.getUUID());
    }

    CompletionStage<AgentResult> requireBound(UUID player) {
        AgentStore store = this.engine.playerStore(player);
        return this.engine.maintenance(() -> store.read(player, data -> {
            if (!data.bound) throw new AgentException("NOT_BOUND");
            return AgentResult.ok("system.result.ready");
        }));
    }

    public void grant(ServerPlayer player, Consumer<AgentResult> feedback) {
        if (!this.beginBindingChange(player, feedback)) return;
        UUID uuid = player.getUUID(), connection = ServerNetworkHandler.connectionId(player);
        Map<String, Set<String>> baselines = new LinkedHashMap<>();
        for (PlayerModule module : this.modules) {
            for (Map.Entry<String, Set<String>> baseline : module.grantBaselines(player).entrySet())
                if (baselines.putIfAbsent(baseline.getKey(), baseline.getValue()) != null)
                    throw new IllegalArgumentException("Duplicate player baseline " + baseline.getKey());
        }
        this.engine.grant(uuid, baselines).whenComplete((result, error) -> this.server.execute(() -> {
            this.bindingChanges.remove(uuid);
            AgentResult answer = error == null ? result : AgentResult.error(AgentEngine.errorCode(error));
            feedback.accept(answer);
            if (this.server.getPlayerList().getPlayer(uuid) != player || !connection.equals(ServerNetworkHandler.connectionId(player)))
                return;
            boolean fresh = answer.messageKey().equals("system.result.bound");
            if (answer.status().equals("SUCCEEDED") && fresh) {
                ServerNetworkHandler.binding(player, AgentResponse.Change.BOUND, answer);
                this.pendingBindingIntroductions.add(uuid);
                this.login(player);
            } else ServerNetworkHandler.notice(player, answer);
        }));
    }

    public void revoke(ServerPlayer player, Consumer<AgentResult> feedback) {
        this.clearPlayerData(player, feedback, false);
    }

    public void reset(ServerPlayer player, Consumer<AgentResult> feedback) {
        this.clearPlayerData(player, feedback, true);
    }

    private void clearPlayerData(ServerPlayer player, Consumer<AgentResult> feedback, boolean reset) {
        if (!this.beginBindingChange(player, feedback)) return;
        UUID uuid = player.getUUID();
        long previous = this.engine.incarnation(uuid);
        CompletionStage<AgentResult> work = reset ? this.engine.reset(uuid) : this.engine.revoke(uuid);
        if (this.engine.incarnation(uuid) != previous) ServerNetworkHandler.connected(player);
        UUID connection = ServerNetworkHandler.connectionId(player);
        work.whenComplete((result, error) -> this.server.execute(() -> {
            this.bindingChanges.remove(uuid);
            AgentResult answer = error == null ? result : AgentResult.error(AgentEngine.errorCode(error));
            if (answer.status().equals("SUCCEEDED")) this.clearSession(uuid);
            feedback.accept(answer);
            if (this.server.getPlayerList().getPlayer(uuid) != player || !connection.equals(ServerNetworkHandler.connectionId(player)))
                return;
            if (!answer.status().equals("SUCCEEDED") && this.engine.incarnation(uuid) != previous) this.login(player);
            if (answer.status().equals("SUCCEEDED"))
                ServerNetworkHandler.binding(player, AgentResponse.Change.REVOKED, answer);
            else if (this.engine.incarnation(uuid) != previous)
                ServerNetworkHandler.binding(player, AgentResponse.Change.INVALIDATED, answer);
            else ServerNetworkHandler.notice(player, answer);
        }));
    }

    private boolean beginBindingChange(ServerPlayer player, Consumer<AgentResult> feedback) {
        if (this.bindingChanges.add(player.getUUID())) return true;
        AgentResult busy = AgentResult.error("BUSY");
        feedback.accept(busy);
        ServerNetworkHandler.notice(player, busy);
        return false;
    }

    void login(ServerPlayer player) {
        this.refreshPlayer(player);
        UUID uuid = player.getUUID();
        long incarnation = this.engine.incarnation(uuid);
        UUID initialization = UUID.randomUUID();
        this.initializations.put(uuid, initialization);
        this.closeSession(uuid);
        // Each feature snapshots game state now, before the asynchronous storage transaction.
        List<PlayerModule.Session> sessions = this.modules.stream().map(module -> module.login(player)).toList();
        this.sessions.put(uuid, sessions);
        AgentStore store = this.engine.playerStore(uuid);
        this.engine.maintenance(() -> {
            if (!store.read(uuid, data -> data.bound)) return false;
            return store.update(uuid, data -> {
                sessions.forEach(session -> session.initialize(data));
                return true;
            });
        }).whenComplete((bound, error) -> this.server.execute(() -> {
            if (this.server.getPlayerList().getPlayer(uuid) != player || !this.engine.current(uuid, incarnation) || !initialization.equals(this.initializations.get(uuid)))
                return;
            this.initializations.remove(uuid);
            if (error != null) {
                this.closeSession(uuid);
                this.ready.remove(uuid);
                this.engine.disableRules(uuid);
                SystemMod.LOGGER.warn("Assistant player initialization failed for {}", uuid, error);
                return;
            }
            if (!bound) {
                this.closeSession(uuid);
                this.ready.remove(uuid);
                this.engine.disableRules(uuid);
                return;
            }
            this.ready.add(uuid);
            sessions.forEach(PlayerModule.Session::ready);
            this.engine.resume(uuid);
        }));
    }

    void earned(ServerPlayer player, Identifier advancement) {
        this.refreshPlayer(player);
        this.modules.forEach(module -> module.advancement(player, advancement));
    }

    private void closeSession(UUID player) {
        List<PlayerModule.Session> sessions = this.sessions.remove(player);
        if (sessions != null) sessions.forEach(PlayerModule.Session::close);
    }

    void close() {
        List.copyOf(this.sessions.keySet()).forEach(this::clearSession);
    }

    void logout(ServerPlayer player) {
        UUID uuid = player.getUUID();
        this.engine.cancel(uuid);
        this.engine.disconnected(uuid);
        this.clearSession(uuid);
        this.usage.disconnected(uuid);
    }

    private void clearSession(UUID uuid) {
        this.pendingBindingIntroductions.remove(uuid);
        this.lastRequest.remove(uuid);
        this.ready.remove(uuid);
        this.closeSession(uuid);
        this.initializations.remove(uuid);
    }
}
