package com.iafenvoy.system.assistant;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.builtin.input.AdvancementInput;
import com.iafenvoy.system.agent.commerce.CommerceTransactions;
import com.iafenvoy.system.agent.store.AgentStore;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.assistant.usage.AssistantUsage;
import com.iafenvoy.system.config.AdvancementRule;
import com.iafenvoy.system.config.file.AdvancementConfig;
import com.iafenvoy.system.network.ServerNetworkHandler;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Per-server binding and player lifecycle; advancement baselines share the same initialization boundary.
 */
public final class AssistantPlayers {
    private final MinecraftServer server;
    private final AgentEngine engine;
    private final CommerceTransactions transactions;
    private final AssistantUsage usage;
    private final Map<UUID, Long> lastRequest = new HashMap<>();
    private final Map<UUID, Set<Identifier>> baselineReady = new HashMap<>();
    private final Map<UUID, List<Identifier>> initializingEvents = new HashMap<>();
    private final Map<UUID, UUID> initializations = new HashMap<>();
    private final Set<UUID> bindingChanges = new HashSet<>();
    private final Set<UUID> pendingBindingIntroductions = new HashSet<>();

    AssistantPlayers(MinecraftServer server, AgentEngine engine, CommerceTransactions transactions, AssistantUsage usage) {
        this.server = server;
        this.engine = engine;
        this.transactions = transactions;
        this.usage = usage;
    }

    void validateRules() {
        for (AdvancementRule rule : AdvancementConfig.INSTANCE.get().rules()) {
            if (!this.engine.modules().call(rule.call()).module().descriptor().event())
                throw new IllegalArgumentException("Unknown event module " + rule.moduleId());
            if (rule.call().value() instanceof AdvancementInput input && (input.advancementId().isPresent() || input.ruleId().isPresent()))
                throw new IllegalArgumentException("Event metadata is supplied by the server");
        }
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
     * The completed login baseline is the main-thread binding cache; notification paths never read files on ticks.
     */
    public boolean notificationsEnabled(ServerPlayer player) {
        UUID uuid = player.getUUID();
        return this.server.getPlayerList().getPlayer(uuid) == player && this.baselineReady.containsKey(uuid)
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
        for (AdvancementRule rule : AdvancementConfig.INSTANCE.get().rules()) {
            AdvancementHolder advancement = this.server.getAdvancements().get(rule.advancementId());
            boolean completed = advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
            baselines.put(rule.id() + "|" + rule.advancementId(), completed ? Set.of(rule.advancementId().toString()) : Set.of());
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
        // Snapshot advancement baselines before enabling rules; buffer new events during asynchronous setup.
        this.refreshPlayer(player);
        UUID uuid = player.getUUID();
        long incarnation = this.engine.incarnation(uuid);
        UUID initialization = UUID.randomUUID();
        this.initializations.put(uuid, initialization);
        AgentStore store = this.engine.playerStore(uuid);
        List<AdvancementRule> rules = AdvancementConfig.INSTANCE.get().rules();
        Map<Identifier, Boolean> completed = new LinkedHashMap<>();
        for (AdvancementRule rule : rules) {
            AdvancementHolder advancement = this.server.getAdvancements().get(rule.advancementId());
            completed.put(rule.id(), advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone());
        }
        this.initializingEvents.put(uuid, new ArrayList<>());
        this.engine.maintenance(() -> {
            if (!store.read(uuid, data -> data.bound)) return null;
            return store.update(uuid, data -> {
                Map<Identifier, Boolean> initial = new LinkedHashMap<>();
                for (AdvancementRule rule : rules) {
                    String baselineId = rule.id() + "|" + rule.advancementId();
                    boolean fresh = !data.baselines.containsKey(baselineId);
                    initial.put(rule.id(), fresh);
                    if (fresh)
                        data.baselines.put(baselineId, completed.get(rule.id()) ? Set.of(rule.advancementId().toString()) : Set.of());
                }
                for (PlayerData.Delivery delivery : data.inbox.values())
                    if ("DELIVERING".equals(delivery.state)) delivery.state = "DELIVERY_UNKNOWN";
                for (PlayerData.Purchase purchase : data.purchases.values())
                    if ("PREPARED".equals(purchase.state) && !this.transactions.active(uuid, purchase.id)) {
                        purchase.state = "PAYMENT_UNKNOWN";
                        purchase.error = "EFFECT_UNKNOWN";
                        PlayerData.Delivery delivery = data.inbox.get(purchase.order.type().entitlement(purchase.id));
                        if (delivery != null) delivery.state = "PAYMENT_UNKNOWN";
                    }
                return initial;
            });
        }).whenComplete((initial, error) -> this.server.execute(() -> {
            if (this.server.getPlayerList().getPlayer(uuid) != player || !this.engine.current(uuid, incarnation) || !initialization.equals(this.initializations.get(uuid)))
                return;
            this.initializations.remove(uuid);
            List<Identifier> delayed = this.initializingEvents.remove(uuid);
            if (error != null) {
                SystemMod.LOGGER.warn("Assistant baseline failed for {}", uuid);
                return;
            }
            if (initial == null) {
                this.baselineReady.remove(uuid);
                this.engine.disableRules(uuid);
                return;
            }
            this.baselineReady.put(uuid, rules.stream().map(AdvancementRule::id).collect(Collectors.toSet()));
            this.engine.enableRules(uuid, this.baselineReady.get(uuid).stream().map(Identifier::toString).collect(Collectors.toSet()));
            for (AdvancementRule rule : rules) {
                if ((!initial.get(rule.id()) && completed.get(rule.id())) || (delayed != null && delayed.contains(rule.advancementId())))
                    this.dispatch(uuid, rule);
            }
            this.engine.resume(uuid);
        }));
    }

    void earned(ServerPlayer player, Identifier advancement) {
        this.refreshPlayer(player);
        UUID uuid = player.getUUID();
        if (this.initializingEvents.containsKey(uuid)) {
            List<Identifier> delayed = this.initializingEvents.get(uuid);
            if (delayed.size() < 128) delayed.add(advancement);
            return;
        }
        for (AdvancementRule rule : AdvancementConfig.INSTANCE.get().rules())
            if (rule.advancementId().equals(advancement) && this.baselineReady.getOrDefault(uuid, Set.of()).contains(rule.id()))
                this.dispatch(uuid, rule);
    }

    private void dispatch(UUID player, AdvancementRule rule) {
        long incarnation = this.engine.incarnation(player);
        AgentStore store = this.engine.playerStore(player);
        this.engine.maintenance(() -> store.read(player, data ->
                        data.baselines.getOrDefault(rule.id() + "|" + rule.advancementId(), Set.of()).contains(rule.advancementId().toString())))
                .thenAccept(baseline -> {
                    if (baseline || !this.engine.current(player, incarnation)) return;
                    TypedPayload<?> input = rule.call();
                    if (input.value() instanceof AdvancementInput value)
                        input = ResultTypes.ADVANCEMENT_ACTION.of(new AdvancementInput(value.featureId(), value.rewardId(),
                                Optional.of(rule.advancementId().toString()), Optional.of(rule.id().toString())));
                    this.engine.trigger(player, rule.id() + "|" + rule.advancementId(), rule.id().toString(), input).thenAccept(result -> {
                        if (result != null && "SUCCEEDED".equals(result.status())) this.server.execute(() -> {
                            ServerPlayer online = this.server.getPlayerList().getPlayer(player);
                            if (online != null && this.engine.current(player, incarnation))
                                ServerNetworkHandler.notice(online, result);
                        });
                    });
                });
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
        this.baselineReady.remove(uuid);
        this.initializingEvents.remove(uuid);
        this.initializations.remove(uuid);
    }
}
