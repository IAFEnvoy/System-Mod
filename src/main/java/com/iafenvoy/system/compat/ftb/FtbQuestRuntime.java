package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.data.store.PlayerData;
import com.iafenvoy.system.module.ContextTypes;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.PlayerModule;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * All configured FTB definitions share one access layer, event bridge and maintenance cycle per server.
 */
final class FtbQuestRuntime implements PlayerModule {
    final FtbQuestAccess access;
    private final Map<UUID, Integer> sessions = new HashMap<>();

    FtbQuestRuntime(ModuleContext context) {
        this.access = new FtbQuestAccess(context.engine(), context.get(ContextTypes.GAME), context.get(ContextTypes.DATABASES).shared());
        context.get(ContextTypes.NARRATION).source(new FtbQuestSpeech(this.access));
    }

    @Override
    public void validate() {
        this.access.notifications.install();
    }

    @Override
    public Session login(ServerPlayer player) {
        UUID id = player.getUUID();
        this.sessions.merge(id, 1, Integer::sum);
        return new Session() {
            private boolean closed;

            @Override
            public void initialize(PlayerData data) {
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
