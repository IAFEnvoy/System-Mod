package com.iafenvoy.system.event.client;

import com.iafenvoy.system.card.CardBuilder;
import com.iafenvoy.system.card.CardData;
import com.iafenvoy.system.card.CardReference;
import net.neoforged.bus.api.Event;

/**
 * Fired on the client game bus before a new card snapshot is frozen and cached.
 * Listeners may enrich the builder synchronously; keep no reference to it after dispatch.
 */
public final class CardBuildEvent extends Event {
    private final CardReference ref;
    private final CardData data;
    private final CardBuilder builder;

    public CardBuildEvent(CardReference ref, CardData data, CardBuilder builder) {
        this.ref = ref;
        this.data = data;
        this.builder = builder;
    }

    public CardReference ref() {
        return this.ref;
    }

    public CardData data() {
        return this.data;
    }

    public CardBuilder builder() {
        return this.builder;
    }
}
