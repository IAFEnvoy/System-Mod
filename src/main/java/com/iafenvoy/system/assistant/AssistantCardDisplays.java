package com.iafenvoy.system.assistant;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.execution.AgentEngine;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.data.card.DisplayCard;
import com.iafenvoy.system.data.card.DisplayCardKey;
import com.iafenvoy.system.data.store.PlayerData;
import com.iafenvoy.system.module.ModuleData;
import com.iafenvoy.system.module.PlayerModule;
import com.iafenvoy.system.module.builtin.CoreSystemModule;
import com.iafenvoy.system.module.builtin.storage.DisplayCardsDataStorage;
import com.iafenvoy.system.network.payload.DisplayCardsS2CPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Module-owned sources rebuild cards on the server thread; only pin references enter storage transactions.
 */
public final class AssistantCardDisplays implements PlayerModule {
    @FunctionalInterface
    public interface Source {
        Optional<CardData> read(ServerPlayer player, String id);
    }

    private record Provider(Identifier owner, Source source) {
    }

    private final MinecraftServer server;
    private final AgentEngine engine;
    private final Map<Identifier, List<Provider>> sources = new LinkedHashMap<>();
    private final Map<UUID, DisplaySession> sessions = new HashMap<>();
    private long nextSession;

    public AssistantCardDisplays(MinecraftServer server, AgentEngine engine) {
        this.server = server;
        this.engine = engine;
    }

    public void register(Identifier owner, Identifier type, Source source) {
        this.sources.computeIfAbsent(type, _ -> new ArrayList<>()).add(new Provider(owner, source));
    }

    private Optional<CardData> read(ServerPlayer player, DisplayCardKey key) {
        AssistantServer runtime = AssistantServer.get(this.server);
        if (runtime == null) return Optional.empty();
        for (Provider provider : this.sources.getOrDefault(key.source(), List.of())) {
            if (!runtime.moduleManager().hasModule(player.getUUID(), provider.owner())) continue;
            try {
                return provider.source().read(player, key.id());
            } catch (RuntimeException error) {
                SystemMod.LOGGER.warn("Unable to refresh displayed card {} for {}", key, player.getUUID(), error);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @Override
    public Session login(ServerPlayer player) {
        DisplaySession session = new DisplaySession(player);
        this.sessions.put(player.getUUID(), session);
        return session;
    }

    public void change(ServerPlayer player, long request, DisplayCardKey key, boolean pinned) {
        DisplaySession session = this.sessions.get(player.getUUID());
        if (session == null || !session.active()) return;
        if (pinned && this.read(player, key).isEmpty()) {
            player.sendSystemMessage(Component.translatable("system.ui.card_display_unavailable"));
            session.refresh(request, true);
            return;
        }
        // Serialize explicit set/remove operations, so quick clicks never let an older file callback win.
        session.writes = session.writes.handle((_, _) -> null).thenCompose(_ -> this.engine.maintenance(() ->
                this.engine.playerStore(player.getUUID()).update(player.getUUID(), data -> {
                    if (session.closed || !data.bound() || !this.engine.current(player.getUUID(), session.incarnation))
                        return null;
                    DisplayCardsDataStorage storage = ModuleData.get(data, CoreSystemModule.DISPLAY_CARDS);
                    storage.setPinned(key, pinned);
                    return storage.getCards();
                }))).whenComplete((keys, error) -> this.server.execute(() -> {
            if (!session.active()) return;
            if (error != null) {
                SystemMod.LOGGER.warn("Unable to persist displayed cards for {}", player.getUUID(), error);
                player.sendSystemMessage(Component.translatable("system.ui.card_display_save_failed"));
            } else if (keys != null) session.keys = keys;
            session.refresh(request, true);
        }));
    }

    @Override
    public void close() {
        for (DisplaySession session : List.copyOf(this.sessions.values())) session.close();
        this.sources.clear();
    }

    private final class DisplaySession implements Session {
        private final ServerPlayer player;
        private final long incarnation;
        private final long sessionId = ++AssistantCardDisplays.this.nextSession;
        private volatile boolean closed;
        private List<DisplayCardKey> keys = List.of();
        private List<DisplayCard> displayed = List.of();
        private CompletionStage<?> writes = CompletableFuture.completedFuture(null);
        private long revision;
        private int ticks;
        private boolean ready;

        private DisplaySession(ServerPlayer player) {
            this.player = player;
            this.incarnation = AssistantCardDisplays.this.engine.incarnation(player.getUUID());
        }

        @Override
        public void initialize(PlayerData data) {
            this.keys = ModuleData.get(data, CoreSystemModule.DISPLAY_CARDS).getCards();
        }

        @Override
        public void ready() {
            this.ready = true;
            this.refresh(0, true);
        }

        private boolean active() {
            return this.ready && !this.closed && AssistantCardDisplays.this.sessions.get(this.player.getUUID()) == this
                    && AssistantCardDisplays.this.server.getPlayerList().getPlayer(this.player.getUUID()) == this.player
                    && AssistantCardDisplays.this.engine.current(this.player.getUUID(), this.incarnation);
        }

        @Override
        public void tick() {
            if (++this.ticks % 20 == 0) this.refresh(0, false);
        }

        private void refresh(long request, boolean force) {
            if (!this.active()) return;
            List<DisplayCard> cards = this.keys.stream().map(key -> new DisplayCard(key, AssistantCardDisplays.this.read(this.player, key))).toList();
            if (!force && cards.equals(this.displayed)) return;
            this.displayed = cards;
            this.player.connection.send(new DisplayCardsS2CPayload(this.sessionId, ++this.revision, request, cards));
        }

        @Override
        public void close() {
            this.closed = true;
            if (AssistantCardDisplays.this.sessions.remove(this.player.getUUID(), this)
                    && AssistantCardDisplays.this.server.getPlayerList().getPlayer(this.player.getUUID()) == this.player)
                this.player.connection.send(new DisplayCardsS2CPayload(this.sessionId, ++this.revision, 0, List.of()));
        }
    }
}
