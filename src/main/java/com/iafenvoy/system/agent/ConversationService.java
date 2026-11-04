package com.iafenvoy.system.agent;

import com.iafenvoy.saturn.util.MiscUtil;
import com.iafenvoy.system.agent.store.AgentStore;
import com.iafenvoy.system.agent.store.Message;
import com.iafenvoy.system.card.CardUpdate;
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

    public CompletionStage<ResultTypes.History> history(UUID player) {
        AgentStore store = this.engine.playerStore(player);
        return this.engine.maintenance(() -> store.read(player, data -> {
            if (!data.bound()) throw new AgentException("NOT_BOUND");
            List<Message> selected = new ArrayList<>();
            int size = 0;
            for (int index = data.conversation.history.size() - 1; index >= 0 && selected.size() < 24; index--) {
                // The page never carries request IDs; oversized results keep complete cards and clipped prose.
                Message entry = data.conversation.history.get(index).withRequestId("");
                if (entry instanceof Message.Assistant assistant
                        && CodecIO.write(AgentResult.CODEC, assistant.result()).length() > 4096)
                    entry = new Message.Assistant("", assistant.time(), clip(assistant.result()));
                size += ResultTypes.History.MESSAGE_CODEC.encodeStart(JsonOps.INSTANCE, entry).getOrThrow().toString().length();
                if (size > 44000) break;
                selected.add(entry);
            }
            return new ResultTypes.History(selected.reversed());
        }));
    }

    /**
     * Keeps a complete JSON result under the per-entry cap; never truncates the middle of a card or action.
     */
    private static AgentResult clip(AgentResult result) {
        List<CardUpdate> cards = new ArrayList<>();
        for (CardUpdate card : result.cards()) {
            cards.add(card);
            if (CodecIO.write(AgentResult.CODEC, presentation(result, cards)).length() > 4096) {
                cards.removeLast();
                break;
            }
        }
        return presentation(result, cards);
    }

    private static AgentResult presentation(AgentResult result, List<CardUpdate> cards) {
        return result.withPresentation(result.messageKey(), MiscUtil.clip(result.text(), 1000), cards, result.silent());
    }

}
