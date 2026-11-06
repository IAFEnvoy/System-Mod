package com.iafenvoy.system.event;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.data.card.CardReference;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Server game-bus event after source validation. Listeners may handle their own string protocol.
 */
public final class CardButtonEvent extends Event {
    private final ServerPlayer player;
    private final CardReference source;
    private final String value;
    private CompletionStage<AgentResult> result;

    public CardButtonEvent(ServerPlayer player, CardReference source, String value) {
        this.player = player;
        this.source = source;
        this.value = value;
    }

    public ServerPlayer player() {
        return this.player;
    }

    public CardReference source() {
        return this.source;
    }

    public String value() {
        return this.value;
    }

    @Nullable
    public CompletionStage<AgentResult> result() {
        return this.result;
    }

    public void handle(AgentResult result) {
        this.handle(CompletableFuture.completedFuture(result));
    }

    public void handle(CompletionStage<AgentResult> result) {
        if (this.result != null) throw new IllegalStateException("Button event already handled");
        this.result = Objects.requireNonNull(result);
    }
}
