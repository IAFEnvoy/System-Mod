package com.iafenvoy.system.assistant.progress;

import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.execution.AgentEngine;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.storage.AgentStore;
import com.iafenvoy.system.assistant.AssistantServer;
import com.iafenvoy.system.assistant.calendar.BusinessCalendar;
import com.iafenvoy.system.assistant.catalog.CatalogAccess;
import com.iafenvoy.system.data.input.RecordClaimInput;
import com.iafenvoy.system.data.output.RecordRewardsOutput;
import com.iafenvoy.system.data.progress.AchievementDefinition;
import com.iafenvoy.system.data.progress.EarnedReward;
import com.iafenvoy.system.data.progress.PlayerFact;
import com.iafenvoy.system.data.store.PlayerData;
import com.iafenvoy.system.data.store.Reward;
import com.iafenvoy.system.event.PlayerFactEvent;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.ModuleData;
import com.iafenvoy.system.module.PlayerModule;
import com.iafenvoy.system.module.builtin.CoreSystemModule;
import com.iafenvoy.system.module.builtin.SpaceSystemModule;
import com.iafenvoy.system.module.builtin.storage.FactDataStorage;
import com.iafenvoy.system.registry.SystemResourceKeys;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trusted game events are captured on the server thread and committed on the storage worker.
 */
public final class PlayerFacts implements PlayerModule {
    public static final Identifier QUEST_COMPLETED = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "quest_completed");
    public static final Identifier SIGNED_IN = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "signed_in");
    public static final Identifier COLLECTED = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "collected");

    public record Award(String id, String category, PersistHolder<Reward> reward, String counter, long target) {
        public Award(String id, String category, PersistHolder<Reward> reward) {
            this(id, category, reward, "", 0);
        }
    }

    private final AgentEngine engine;

    private record Pending(long incarnation, Identifier system, PlayerFact fact, List<Award> awards) {
    }

    private final Map<UUID, Map<String, Pending>> pending = new ConcurrentHashMap<>();
    private final Set<UUID> running = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> retryAt = new ConcurrentHashMap<>();
    private volatile boolean closed;

    private PlayerFacts(AgentEngine engine) {
        this.engine = engine;
        NeoForge.EVENT_BUS.register(this);
    }

    public static PlayerFacts service(ModuleContext context) {
        return context.sharedRuntime(PlayerFacts.class, () -> new PlayerFacts(context.engine()));
    }

    public static List<Award> prepare(ServerPlayer player, PlayerFact fact) {
        if (!player.level().getServer().isSameThread())
            throw new IllegalStateException("Facts require the server thread");
        List<Award> awards = new ArrayList<>(player.registryAccess().lookupOrThrow(SystemResourceKeys.FACT_REWARD).listElements()
                .filter(holder -> holder.value().matches(fact) && CatalogAccess.visible(player, holder.value()) && CatalogAccess.usable(player, holder.value()))
                .map(holder -> {
                    Identifier id = holder.key().identifier();
                    return new Award(holder.value().entitlement(id, fact, BusinessCalendar.zone()), fact.event().toString(),
                            new PersistHolder<>(id, holder.value().reward()));
                }).toList());
        player.registryAccess().lookupOrThrow(SystemResourceKeys.ACHIEVEMENT).listElements()
                .filter(holder -> holder.value().matches(fact) && CatalogAccess.visible(player, holder.value()) && CatalogAccess.usable(player, holder.value()))
                .forEach(holder -> {
                    AchievementDefinition definition = holder.value();
                    awards.add(new Award(AchievementDefinition.entitlement(holder.key().identifier()),
                            "achievement", new PersistHolder<>(holder.key().identifier(), definition.reward()), definition.counter(), definition.target()));
                });
        return List.copyOf(awards);
    }

    /**
     * Called inside the same transaction as the feature's own progress and reward snapshot.
     */
    public static void record(PlayerData data, PlayerFact fact, List<Award> awards) {
        FactDataStorage storage = ModuleData.get(data, CoreSystemModule.FACTS);
        if (!storage.record(fact)) return;
        for (Award award : awards)
            if (award.counter().isEmpty() || storage.counts().getOrDefault(award.counter(), 0L) >= award.target())
                storage.earn(award.id(), award.category(), award.reward(), fact.timestamp());
    }

    public static RecordRewardsOutput query(ExecutionContext context, int offset) {
        return context.store().read(context.player(), data -> {
            FactDataStorage storage = ModuleData.get(data, CoreSystemModule.FACTS);
            List<EarnedReward> all = storage.rewards().values().stream().sorted(Comparator.comparing(EarnedReward::transferred)
                    .thenComparing(Comparator.comparingLong(EarnedReward::created).reversed()).thenComparing(EarnedReward::id)).toList();
            int start = Math.clamp(offset, 0, Math.max(0, ((all.size() - 1) / RecordRewardsOutput.PAGE_SIZE) * RecordRewardsOutput.PAGE_SIZE));
            int end = Math.min(all.size(), start + RecordRewardsOutput.PAGE_SIZE);
            return new RecordRewardsOutput(all.subList(start, end), start, end < all.size(), all.stream().filter(reward -> !reward.transferred()).count(), storage.counts());
        });
    }

    public static RecordRewardsOutput claim(ExecutionContext context, RecordClaimInput input) {
        context.store().update(context.player(), data -> {
            context.check();
            ModuleData.get(data, CoreSystemModule.FACTS).claim(input.entitlement(), ModuleData.get(data, SpaceSystemModule.STORAGE));
            return null;
        });
        return query(context, 0);
    }

    /**
     * The caller is a trusted server adapter; clients and model tools cannot submit facts.
     */
    public static void emit(ServerPlayer player, PlayerFact fact) {
        NeoForge.EVENT_BUS.post(new PlayerFactEvent(player, fact));
    }

    @SubscribeEvent
    public void event(PlayerFactEvent event) {
        ServerPlayer player = event.player();
        AssistantServer runtime = AssistantServer.get(player.level().getServer());
        if (runtime == null || runtime.engine() != this.engine || !runtime.moduleManager().hasType(player.getUUID(), CoreSystemModule.CODEC))
            return;
        UUID uuid = player.getUUID();
        Identifier system = runtime.moduleManager().system(uuid).orElse(null);
        if (system == null) return;
        Pending captured = new Pending(this.engine.incarnation(uuid), system, event.fact(), prepare(player, event.fact()));
        this.pending.computeIfAbsent(uuid, _ -> new ConcurrentHashMap<>()).compute(event.fact().key(), (_, previous) ->
                previous == null || previous.incarnation() != captured.incarnation() ? captured : previous);
    }

    @Override
    public void tick() {
        int submitted = 0;
        for (Map.Entry<UUID, Map<String, Pending>> entry : this.pending.entrySet()) {
            UUID uuid = entry.getKey();
            Map<String, Pending> queue = entry.getValue();
            queue.values().removeIf(value -> !this.engine.current(uuid, value.incarnation()));
            if (queue.isEmpty()) {
                this.pending.remove(uuid, queue);
                continue;
            }
            if (this.running.contains(uuid) || this.retryAt.getOrDefault(uuid, 0L) > System.currentTimeMillis())
                continue;
            if (submitted++ >= 4) break;
            this.running.add(uuid);
            List<Pending> batch = queue.values().stream().limit(64).toList();
            AgentStore store = this.engine.playerStore(uuid);
            this.engine.maintenance(() -> store.update(uuid, data -> {
                if (this.closed) return null;
                for (Pending captured : batch) {
                    if (this.engine.current(uuid, captured.incarnation()) && data.systems.current().filter(captured.system()::equals).isPresent())
                        record(data, captured.fact(), captured.awards());
                }
                return null;
            })).whenComplete((_, error) -> {
                if (error == null) {
                    batch.forEach(captured -> queue.remove(captured.fact().key(), captured));
                    this.retryAt.remove(uuid);
                } else {
                    this.retryAt.put(uuid, System.currentTimeMillis() + 5000);
                    SystemMod.LOGGER.warn("Unable to persist player facts; retrying: player={}, count={}", uuid, batch.size(), error);
                }
                this.running.remove(uuid);
            });
        }
    }

    @Override
    public void close() {
        this.closed = true;
        NeoForge.EVENT_BUS.unregister(this);
        this.pending.clear();
        this.retryAt.clear();
    }
}
