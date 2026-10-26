package com.iafenvoy.system.agent;

import com.iafenvoy.system.agent.store.AgentStore;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.util.AgentCodecs;
import com.iafenvoy.system.util.CodecIO;
import com.iafenvoy.system.util.Json;
import java.util.*;
import java.util.concurrent.*;

/** Conversation storage operations share the engine's lifecycle-aware store and maintenance queue. */
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
            if (!data.bound) throw new AgentException("NOT_BOUND");
            data.memoryEpoch++;
            data.history.clear();
            data.requests.clear();
            data.requestInputs.clear();
            this.engine.router().clear(player);
            return AgentResult.ok("system.result.forgotten");
        }));
    }

    public CompletionStage<AgentResult> preferences(UUID player, boolean history, boolean external) {
        this.engine.cancel(player);
        AgentStore store = this.engine.playerStore(player);
        return this.engine.personalMaintenance(player, () -> store.update(player, data -> {
            if (!data.bound) throw new AgentException("NOT_BOUND");
            data.memoryEpoch++;
            data.saveHistory = history;
            data.externalModel = external;
            this.engine.router().clear(player);
            if (!history) {
                data.history.clear();
                data.requests.clear();
                data.requestInputs.clear();
            }
            return AgentResult.data("system.result.preferences", ResultTypes.PREFERENCES.of(new ResultTypes.Preferences(history, external)));
        }));
    }

    public CompletionStage<AgentResult> history(UUID player) {
        AgentStore store = this.engine.playerStore(player);
        return this.engine.personalMaintenance(player, () -> store.read(player, data -> {
            if (!data.bound) throw new AgentException("NOT_BOUND");
            List<PlayerData.Message> selected = new ArrayList<>();
            int size = 0;
            for (int index = data.history.size() - 1; index >= 0 && selected.size() < 24; index--) {
                PlayerData.Message message = data.history.get(index);
                String text = message.text();
                if (message.role().equals("assistant") && text.length() > 4096) {
                    AgentResult result = CodecIO.parse(AgentResult.CODEC, text, 1_048_576);
                    // Keep a complete JSON result; never truncate the middle of a card or action.
                    List<AgentResult.Card> cards = new ArrayList<>();
                    for (AgentResult.Card card : result.cards()) {
                        cards.add(card);
                        if (CodecIO.write(AgentResult.CODEC, new AgentResult(result.status(), result.messageKey(), Json.clip(result.text(), 1000), cards)).length() > 4096) {
                            cards.removeLast();
                            break;
                        }
                    }
                    text = CodecIO.write(AgentResult.CODEC, new AgentResult(result.status(), result.messageKey(), Json.clip(result.text(), 1000), cards));
                }
                PlayerData.Message entry = new PlayerData.Message("", message.role(), message.time(), text);
                size += AgentCodecs.encode(ResultTypes.History.MESSAGE_CODEC, entry).toString().length();
                if (size > 44000) break;
                selected.add(entry);
            }
            return AgentResult.data("system.result.history", ResultTypes.HISTORY.of(new ResultTypes.History(selected.reversed())));
        }));
    }

}
