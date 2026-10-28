package com.iafenvoy.system.agent;

import com.iafenvoy.saturn.util.MiscUtil;
import com.iafenvoy.system.agent.store.AgentStore;
import com.iafenvoy.system.agent.store.PlayerData;
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
            List<PlayerData.Message> selected = new ArrayList<>();
            int size = 0;
            for (int index = data.conversation.history.size() - 1; index >= 0 && selected.size() < 24; index--) {
                PlayerData.Message message = data.conversation.history.get(index);
                String text = message.text();
                if (message.role().equals("assistant") && text.length() > 4096) {
                    AgentResult result = CodecIO.parse(AgentResult.CODEC, text);
                    // Keep a complete JSON result; never truncate the middle of a card or action.
                    List<CardUpdate> cards = new ArrayList<>();
                    for (CardUpdate card : result.cards()) {
                        cards.add(card);
                        if (CodecIO.write(AgentResult.CODEC, result.withPresentation(result.messageKey(), MiscUtil.clip(result.text(), 1000), cards, result.silent())).length() > 4096) {
                            cards.removeLast();
                            break;
                        }
                    }
                    text = CodecIO.write(AgentResult.CODEC, result.withPresentation(result.messageKey(), MiscUtil.clip(result.text(), 1000), cards, result.silent()));
                }
                PlayerData.Message entry = new PlayerData.Message("", message.role(), message.time(), text);
                size += ResultTypes.History.MESSAGE_CODEC.encodeStart(JsonOps.INSTANCE, entry).getOrThrow().toString().length();
                if (size > 44000) break;
                selected.add(entry);
            }
            return new ResultTypes.History(selected.reversed());
        }));
    }

}
