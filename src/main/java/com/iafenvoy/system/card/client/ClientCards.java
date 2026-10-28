package com.iafenvoy.system.card.client;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.card.*;

import java.util.*;

/**
 * Connection-local snapshots are shared by history, current reply and the notification queue.
 */
public final class ClientCards {
    private static final Map<UUID, Entry> CARDS = new HashMap<>();
    private static final Map<UUID, Long> REMOVED = new HashMap<>();

    private record Entry(CardRef ref, CardData data, CardView view, boolean active) {
    }

    public record Choice(CardRef source, CardButton button) {
        public String labelKey() {
            return this.button.labelKey();
        }

        public void execute() {
            CardController.activate(this.source, this.button);
        }
    }

    private ClientCards() {
    }

    public static void clear() {
        CARDS.clear();
        REMOVED.clear();
    }

    public static boolean apply(CardUpdate update, boolean history) {
        Entry previous = CARDS.get(update.ref().id());
        if (history && previous != null) return false;
        if (REMOVED.getOrDefault(update.ref().id(), -1L) >= update.ref().revision()) return false;
        if (previous != null && previous.ref.revision() >= update.ref().revision()) return false;
        if (update.mode() == CardUpdate.Mode.REMOVE) {
            REMOVED.put(update.ref().id(), update.ref().revision());
            if (previous != null)
                CARDS.put(update.ref().id(), new Entry(update.ref(), previous.data, previous.view, false));
            return true;
        }
        if (update.mode() == CardUpdate.Mode.SHOW || history) {
            CardData data = update.data().orElseThrow();
            CARDS.put(update.ref().id(), new Entry(update.ref(), data, data.buildCard().build(), !history));
        } else {
            if (previous == null) return false;
            CardData data = update.data().orElse(previous.data);
            CARDS.put(update.ref().id(), new Entry(update.ref(), data, update.data().isPresent() ? data.buildCard().build() : previous.view, previous.active));
        }
        return true;
    }

    public static boolean active(CardRef ref) {
        Entry entry = CARDS.get(ref.id());
        return entry != null && entry.active && entry.ref.equals(ref);
    }

    public static CardUpdate latest(CardUpdate update) {
        Entry entry = CARDS.get(update.ref().id());
        return entry == null ? update : new CardUpdate(entry.ref, CardUpdate.Mode.SHOW, Optional.of(entry.data));
    }

    public static CardView view(CardUpdate update) {
        Entry entry = CARDS.get(update.ref().id());
        return entry == null ? update.data().orElseThrow().buildCard().build() : entry.view;
    }

    public static AgentResult latest(AgentResult result) {
        return result.withPresentation(result.messageKey(), result.text(), result.cards().stream().map(ClientCards::latest).toList(), result.silent());
    }

    public static List<Choice> choices(AgentResult result) {
        List<Choice> choices = new ArrayList<>();
        for (CardUpdate original : result.cards()) {
            CardUpdate update = latest(original);
            if (active(update.ref()))
                for (CardButton button : view(update).buttons()) choices.add(new Choice(update.ref(), button));
        }
        return List.copyOf(choices);
    }
}
