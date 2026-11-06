package com.iafenvoy.system.module.client;

import com.iafenvoy.system.data.card.DisplayCard;
import com.iafenvoy.system.data.card.DisplayCardKey;
import com.iafenvoy.system.network.payload.DisplayCardC2SPayload;
import com.iafenvoy.system.network.payload.DisplayCardsS2CPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client mirrors only acknowledged server references and snapshots; persistence belongs to the core DataStorage.
 */
public final class DisplayCardCache {
    private List<DisplayCard> cards = List.of();
    private final Map<Long, Pending> requests = new HashMap<>();
    private long session;
    private long serverRevision;
    private long nextRequest;
    private long revision;
    private long pinRevision;

    private record Pending(DisplayCardKey key, long deadline) {
    }

    public List<DisplayCard> cards() {
        return this.cards;
    }

    public boolean contains(DisplayCardKey key) {
        return this.cards.stream().anyMatch(card -> card.key().equals(key));
    }

    public boolean pending(DisplayCardKey key) {
        return this.requests.values().stream().anyMatch(request -> request.key().equals(key));
    }

    public long revision() {
        return this.revision;
    }

    public long pinRevision() {
        return this.pinRevision;
    }

    public void toggle(DisplayCardKey key) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null || this.session == 0 || this.pending(key)) return;
        long request = ++this.nextRequest;
        this.requests.put(request, new Pending(key, System.currentTimeMillis() + 10000));
        this.pinRevision++;
        connection.send(new DisplayCardC2SPayload(request, key, !this.contains(key)));
    }

    public void receive(DisplayCardsS2CPayload payload) {
        if (payload.session() < this.session) return;
        if (this.session != payload.session()) {
            this.session = payload.session();
            this.serverRevision = 0;
            this.requests.clear();
            this.pinRevision++;
        }
        if (this.requests.remove(payload.request()) != null) this.pinRevision++;
        if (payload.revision() <= this.serverRevision) return;
        this.serverRevision = payload.revision();
        if (!this.cards.stream().map(DisplayCard::key).toList().equals(payload.cards().stream().map(DisplayCard::key).toList()))
            this.pinRevision++;
        if (!this.cards.equals(payload.cards())) {
            this.cards = payload.cards();
            this.revision++;
        }
    }

    public void tick() {
        if (this.requests.values().removeIf(request -> request.deadline() <= System.currentTimeMillis()))
            this.pinRevision++;
    }

    public void clear() {
        this.cards = List.of();
        this.requests.clear();
        this.session = this.serverRevision = 0;
        this.revision++;
        this.pinRevision++;
    }
}
