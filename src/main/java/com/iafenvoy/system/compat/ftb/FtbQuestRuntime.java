package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.assistant.quest.QuestProvider;
import com.iafenvoy.system.assistant.quest.QuestWorkflows;
import com.iafenvoy.system.data.store.PlayerData;
import com.iafenvoy.system.module.ContextTypes;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.ModuleData;
import com.iafenvoy.system.module.PlayerModule;
import com.iafenvoy.system.module.builtin.CoreSystemModule;
import com.iafenvoy.system.module.builtin.storage.FactDataStorage;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * All configured FTB definitions share one access layer, event bridge and maintenance cycle per server.
 */
final class FtbQuestRuntime implements PlayerModule {
    final FtbQuestAccess access;
    final QuestProvider provider;
    private final Map<UUID, Integer> sessions = new HashMap<>();

    FtbQuestRuntime(ModuleContext context) {
        this.access = new FtbQuestAccess(context.engine(), context.get(ContextTypes.GAME), context.get(ContextTypes.DATABASES).shared(), QuestWorkflows.service(context));
        this.provider = new FtbQuestProvider(this.access);
    }

    @Override
    public void validate() {
        this.access.notifications.install();
    }

    @Override
    public Session login(ServerPlayer player) {
        UUID id = player.getUUID();
        long joined = System.currentTimeMillis();
        this.sessions.merge(id, 1, Integer::sum);
        return new Session() {
            private boolean closed;
            private long observedSince;
            private Set<String> seen = Set.of();

            @Override
            public void initialize(PlayerData data) {
                FactDataStorage facts = ModuleData.get(data, CoreSystemModule.FACTS);
                this.observedSince = facts.observe(FtbQuestProvider.SOURCE.id().toString(), joined);
                this.seen = facts.seen();
            }

            @Override
            public void ready() {
                FtbQuestRuntime.this.access.notifications.observe(player, this.observedSince, this.seen);
            }

            @Override
            public void close() {
                if (this.closed) return;
                this.closed = true;
                // Removing one definition must not clear another active definition's reward/list references.
                int remaining = FtbQuestRuntime.this.sessions.getOrDefault(id, 1) - 1;
                if (remaining > 0) FtbQuestRuntime.this.sessions.put(id, remaining);
                else {
                    FtbQuestRuntime.this.sessions.remove(id);
                    FtbQuestRuntime.this.access.notifications.remove(player);
                }
            }
        };
    }

    @Override
    public void tick() {
        this.access.notifications.tick();
    }

    @Override
    public void close() {
        this.access.notifications.close();
        this.access.knowledge.invalidate();
        this.sessions.clear();
    }
}
