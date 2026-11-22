package com.iafenvoy.system.assistant.signin;

import com.iafenvoy.assistant.execution.AgentLifecycle;
import com.iafenvoy.assistant.storage.AgentStore;
import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.system.agent.execution.AgentEngine;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.data.progress.PlayerFact;
import com.iafenvoy.system.data.progress.SignInProgress;
import com.iafenvoy.system.data.progress.SignInRules;
import com.iafenvoy.system.data.store.PlayerData;
import com.iafenvoy.system.data.store.Reward;
import com.iafenvoy.system.data.store.SignInArchive;
import com.iafenvoy.system.module.ModuleData;
import com.iafenvoy.system.module.PlayerModule;
import com.iafenvoy.system.module.builtin.CoreSystemModule;
import com.iafenvoy.system.module.builtin.SignInSystemModule;
import com.iafenvoy.system.module.builtin.SpaceSystemModule;
import com.iafenvoy.system.module.builtin.storage.FactDataStorage;
import com.iafenvoy.system.module.builtin.storage.SignInDataStorage;
import com.iafenvoy.system.module.builtin.storage.SpaceDataStorage;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * Daily deduplication survives snapshot compaction and delivered-reward cleanup.
 */
public final class SignIns implements PlayerModule {
    public static final int RECENT_RECORDS = 32;
    private final AgentEngine engine;
    private final SignInArchive archive;

    public SignIns(AgentEngine engine, SignInArchive archive) {
        this.engine = engine;
        this.archive = archive;
        engine.lifecycle((player, event) -> {
            if (event == AgentLifecycle.EventType.REVOKED) this.archive.delete(player);
        });
    }

    public PersistHolder<Reward> reward(ExecutionContext context, String day) {
        return context.store().read(context.player(), data -> this.reward(context.store(), context.player(), data, day).orElse(null));
    }

    public Optional<PersistHolder<Reward>> reward(AgentStore<PlayerData> store, UUID player, PlayerData data, String day) {
        SignInDataStorage signIn = ModuleData.get(data, SignInSystemModule.STORAGE);
        return Optional.ofNullable(signIn.getReward(day)).or(() -> this.archive.get(store, player, signIn.archiveScope(), day));
    }

    public Optional<PersistHolder<Reward>> entitlement(AgentStore<PlayerData> store, UUID player, PlayerData data, String entitlement) {
        return entitlement.startsWith("daily:") ? this.reward(store, player, data, entitlement.substring(6)) : Optional.empty();
    }

    /**
     * Write each archive before dropping its live snapshot; failed player saves can safely retry the same archive.
     */
    public void compact(AgentStore<PlayerData> store, UUID player, PlayerData data) {
        this.compact(store, player, data, RECENT_RECORDS);
    }

    /**
     * Archive only previously committed days, keeping the new entitlement inside the player transaction.
     */
    public void prepareCredit(AgentStore<PlayerData> store, UUID player, PlayerData data) {
        this.compact(store, player, data, RECENT_RECORDS - 1);
    }

    private void compact(AgentStore<PlayerData> store, UUID player, PlayerData data, int retained) {
        SignInDataStorage signIn = ModuleData.get(data, SignInSystemModule.STORAGE);
        for (String day : signIn.getRewards().keySet().stream().sorted(Comparator.reverseOrder()).skip(retained).toList()) {
            this.archive.put(store, player, signIn.archiveScope(), day, signIn.getReward(day));
            signIn.archiveReward(day);
        }
        SpaceDataStorage space = ModuleData.get(data, SpaceSystemModule.STORAGE);
        Set<String> remaining = new HashSet<>();
        space.getEntries().values().forEach(entry -> entry.entitlement().ifPresent(remaining::add));
        for (String id : space.streamDeliveries().filter(delivery -> delivery.id.startsWith("daily:") && "DELIVERED".equals(delivery.state))
                .map(delivery -> delivery.id).filter(id -> !remaining.contains(id) && !signIn.hasReward(id.substring(6))).toList()) {
            if (this.entitlement(store, player, data, id).isPresent()) space.removeDelivery(id);
        }
    }

    public static void milestones(PlayerData data, Identifier definition, SignInRules rules, SignInProgress progress, long now) {
        FactDataStorage storage = ModuleData.get(data, CoreSystemModule.FACTS);
        for (SignInRules.Rule rule : rules.milestones()) {
            long value = switch (rule.metric()) {
                case TOTAL -> progress.total();
                case STREAK -> progress.streak();
                case WEEK -> progress.weekly();
                case MONTH -> progress.monthly();
            };
            if (value < rule.target()) continue;
            String period = switch (rule.metric()) {
                case WEEK -> progress.week();
                case MONTH -> progress.month();
                default -> "once";
            };
            // Stable IDs, rather than the editable target or reward, identify already earned milestones.
            String id = "signin:" + PlayerFact.key(definition.toString(), rule.id().toString(), period);
            storage.earn(id, "signin", new PersistHolder<>(rule.id(), rule.reward()), now);
        }
    }

    @Override
    public Session login(ServerPlayer player) {
        UUID uuid = player.getUUID();
        AgentStore<PlayerData> store = this.engine.playerStore(uuid);
        return data -> this.compact(store, uuid, data);
    }
}
