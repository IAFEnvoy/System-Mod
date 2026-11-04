package com.iafenvoy.system.agent;

import com.iafenvoy.saturn.util.MiscUtil;
import com.iafenvoy.system.agent.store.AgentStore;
import com.iafenvoy.system.agent.store.Message;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.util.CodecIO;
import com.mojang.serialization.JsonOps;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Conversation storage operations share the engine's lifecycle-aware store and maintenance queue.
 */
final class ConversationService {
    /**
     * One entry may carry a whole card snapshot; 4 KiB truncated real cards (the player card is routinely larger).
     * The page budget is what actually bounds a history response, and the newest turn is always included.
     */
    private static final int MAX_ENTRY_CHARS = 128 * 1024;
    private static final int MAX_PAGE_CHARS = 1024 * 1024;
    private static final int MAX_ENTRIES = 24;
    private static final int MAX_PROSE_CHARS = 1000;
    private final AgentEngine engine;

    ConversationService(AgentEngine engine) {
        this.engine = engine;
    }

    public CompletionStage<AgentResult> forget(UUID player) {
        // Advance the memory epoch so in-flight chat cannot restore history or act on cleared context.
        this.engine.cancel(player);
        AgentStore store = this.engine.playerStore(player);
        return this.engine.personalMaintenance(player, () -> store.update(player, data -> {
            if (!data.bound()) throw new AgentException("NOT_BOUND");
            data.conversation.clear();
            this.engine.router().clear(player);
            return AgentResult.ok("system.result.forgotten");
        }));
    }

    public CompletionStage<AgentResult> preferences(UUID player, boolean history, boolean external) {
        this.engine.cancel(player);
        AgentStore store = this.engine.playerStore(player);
        return this.engine.personalMaintenance(player, () -> store.update(player, data -> {
            if (!data.bound()) throw new AgentException("NOT_BOUND");
            data.saveHistory = history;
            data.externalModel = external;
            this.engine.router().clear(player);
            if (!history) data.conversation.clear();
            else data.conversation.memoryEpoch++;
            return AgentResult.ok("system.result.preferences");
        }));
    }

    public CompletionStage<History> history(UUID player) {
        AgentStore store = this.engine.playerStore(player);
        return this.engine.maintenance(() -> store.read(player, data -> {
            if (!data.bound()) throw new AgentException("NOT_BOUND");
            List<Message> selected = new ArrayList<>();
            int size = 0;
            for (int index = data.conversation.history.size() - 1; index >= 0 && selected.size() < MAX_ENTRIES; index--) {
                // The page never carries request IDs; oversized results keep complete cards and clipped prose.
                Message entry = data.conversation.history.get(index).withRequestId("");
                if (entry instanceof Message.Assistant assistant
                        && CodecIO.write(AgentResult.CODEC, assistant.result()).length() > MAX_ENTRY_CHARS)
                    entry = new Message.Assistant("", assistant.time(), clip(assistant.result()));
                int length = Message.CODEC.encodeStart(JsonOps.INSTANCE, entry).getOrThrow().toString().length();
                // The newest turn is always sent; an entry that does not fit only stops older ones.
                if (!selected.isEmpty() && size + length > MAX_PAGE_CHARS) break;
                size += length;
                selected.add(entry);
            }
            return new History(selected.reversed());
        }));
    }

    /**
     * Keeps a complete JSON result under the per-entry cap; never truncates the middle of a card or action.
     * The first card always survives: an entry without cards renders as an empty row on the client.
     */
    private static AgentResult clip(AgentResult result) {
        List<CardUpdate> cards = new ArrayList<>();
        for (CardUpdate card : result.cards()) {
            cards.add(card);
            if (cards.size() > 1 && CodecIO.write(AgentResult.CODEC, presentation(result, cards)).length() > MAX_ENTRY_CHARS) {
                cards.removeLast();
                break;
            }
        }
        return presentation(result, cards);
    }

    private static AgentResult presentation(AgentResult result, List<CardUpdate> cards) {
        return result.withPresentation(result.messageKey(), MiscUtil.clip(result.text(), MAX_PROSE_CHARS), cards, result.silent());
    }

}
