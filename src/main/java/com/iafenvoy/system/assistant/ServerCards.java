package com.iafenvoy.system.assistant;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.card.CardRef;
import com.iafenvoy.system.card.CardUpdate;

import java.util.*;

/**
 * Connection-scoped correlation only; workflows still validate ownership, module access and reward references.
 */
public final class ServerCards {
    private static final Map<UUID, Map<UUID, Version>> PLAYERS = new HashMap<>();

    private static final class Version {
        long issued;
        long reserved;

        Version(long revision) {
            this.issued = this.reserved = revision;
        }
    }

    public record Ticket(UUID player, CardRef source, long revision, Object generation) {
    }

    private ServerCards() {
    }

    public static synchronized void clear(UUID player) {
        PLAYERS.remove(player);
    }

    public static synchronized void publish(UUID player, AgentResult result) {
        Map<UUID, Version> cards = PLAYERS.computeIfAbsent(player, _ -> new HashMap<>());
        for (CardUpdate update : result.cards()) {
            if (update.mode() == CardUpdate.Mode.REMOVE) cards.remove(update.ref().id());
            else cards.compute(update.ref().id(), (_, previous) -> {
                if (previous == null) return new Version(update.ref().revision());
                previous.issued = Math.max(previous.issued, update.ref().revision());
                previous.reserved = Math.max(previous.reserved, previous.issued);
                return previous;
            });
        }
    }

    public static synchronized Ticket begin(UUID player, CardRef source) {
        Map<UUID, Version> cards = PLAYERS.get(player);
        Version version = cards == null ? null : cards.get(source.id());
        if (version == null || version.issued != source.revision()) throw new AgentException("STALE_REFERENCE");
        return new Ticket(player, source, ++version.reserved, version);
    }

    public static synchronized AgentResult finish(Ticket ticket, AgentResult result) {
        Map<UUID, Version> cards = PLAYERS.get(ticket.player());
        Version current = cards == null ? null : cards.get(ticket.source().id());
        if (current != ticket.generation() || current.reserved != ticket.revision())
            throw new AgentException("CANCELLED");
        if (!result.silent() || !result.isSuccess()) return result;
        CardRef ref = new CardRef(ticket.source().id(), ticket.revision());
        List<CardUpdate> updates = new ArrayList<>();
        if (result.cards().isEmpty()) updates.add(new CardUpdate(ref, CardUpdate.Mode.REMOVE, Optional.empty()));
        else {
            updates.add(new CardUpdate(ref, CardUpdate.Mode.REPLACE, result.cards().getFirst().data()));
            updates.addAll(result.cards().subList(1, result.cards().size()));
        }
        return result.withPresentation(result.messageKey(), result.text(), updates, true);
    }
}
