package com.iafenvoy.system.assistant;

import com.iafenvoy.system.agent.AgentRequest;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.util.TypeDispatch;
import com.iafenvoy.system.util.codec.CodecIO;

import java.util.*;
import java.util.function.BiFunction;

/**
 * Immutable connection-local conversation; EMPTY is reusable and never mutated by requests or notices.
 */
public record AssistantClientState(List<ChatEntry> entries, Set<String> pending, ResultTypes.ClientState serverState,
                                   ChatEntry current) {
    public static final AssistantClientState EMPTY = new AssistantClientState(List.of(), Set.of(),
            new ResultTypes.ClientState(true, true, false, List.of()), null);
    private static final TypeDispatch<ResultTypes.ClientState, ResultTypes.ClientState> CARD_UPDATES =
            new TypeDispatch<ResultTypes.ClientState, ResultTypes.ClientState>((previous, _) -> previous)
                    .register(ResultTypes.ClientState.class, (_, state) -> state)
                    .register(ResultTypes.Preferences.class, (previous, preferences) -> preferences(previous, preferences.historySaved(), preferences.externalModel()))
                    .register(ResultTypes.SignInStatus.class, (previous, status) -> preferences(previous, status.historySaved(), status.externalModel()));
    private static final Map<String, BiFunction<AssistantClientState, Reception, AssistantClientState>> RESULT_HANDLERS = Map.of(
            "system.result.state", (state, a) -> state,
            "system.result.forgotten", (state, reception) -> new AssistantClientState(List.of(), state.pending, state.serverState, state.current).append(reception),
            "system.result.history", AssistantClientState::restoreHistory);

    public AssistantClientState {
        entries = List.copyOf(entries.subList(Math.max(0, entries.size() - 64), entries.size()));
        pending = Set.copyOf(pending);
    }

    public record ChatEntry(boolean player, String text, AgentResult result) {
    }

    private record Reception(AgentResult result, boolean pending, boolean notification) {
    }

    public boolean busy() {
        return !this.pending.isEmpty();
    }

    public boolean saveHistory() {
        return this.serverState.historySaved();
    }

    public boolean externalModel() {
        return this.serverState.externalModel();
    }

    public boolean questAvailable() {
        return this.serverState.questAvailable();
    }

    public List<String> commands() {
        return this.serverState.commands();
    }

    public List<AgentResult.Action> currentActions() {
        return this.current == null || this.current.result() == null ? List.of() : this.current.result().cards().stream().flatMap(card -> card.actions().stream()).toList();
    }

    AssistantClientState local(AgentResult result) {
        ChatEntry current = new ChatEntry(false, "", result);
        List<ChatEntry> entries = new ArrayList<>(this.entries);
        entries.add(current);
        return new AssistantClientState(entries, this.pending, this.serverState, current);
    }

    AssistantClientState requested(String id, AgentRequest request) {
        List<ChatEntry> entries = new ArrayList<>(this.entries);
        if (!request.text().isBlank()) entries.add(new ChatEntry(true, request.text(), null));
        Set<String> pending = new HashSet<>(this.pending);
        pending.add(id);
        ChatEntry current = request == AgentRequest.Control.STATE ? this.current : new ChatEntry(false, request.text(), null);
        return new AssistantClientState(entries, pending, this.serverState, current);
    }

    AssistantClientState complete(String id) {
        if (!this.pending.contains(id)) return this;
        Set<String> pending = new HashSet<>(this.pending);
        pending.remove(id);
        return new AssistantClientState(this.entries, pending, this.serverState, this.current);
    }

    AssistantClientState withoutPending() {
        return this.pending.isEmpty() ? this : new AssistantClientState(this.entries, Set.of(), this.serverState, this.current);
    }

    AssistantClientState receive(String id, AgentResult result, boolean notification) {
        Reception reception = new Reception(result, this.pending.contains(id), notification);
        AssistantClientState completed = this.complete(id);
        ResultTypes.ClientState serverState = completed.serverState;
        for (AgentResult.Card card : result.cards()) serverState = CARD_UPDATES.apply(serverState, card.data().value());
        AssistantClientState updated = new AssistantClientState(completed.entries, completed.pending, serverState, completed.current);
        return RESULT_HANDLERS.getOrDefault(result.messageKey(), AssistantClientState::append).apply(updated, reception);
    }

    private AssistantClientState append(Reception reception) {
        List<ChatEntry> entries = new ArrayList<>(this.entries);
        entries.add(new ChatEntry(false, "", reception.result()));
        ChatEntry current = this.current;
        // Event notices enter history without replacing the active reply or its numbered actions.
        if (!reception.notification() && (reception.pending() || !this.busy()))
            current = new ChatEntry(false, reception.pending() && current != null ? current.text() : "", reception.result());
        return new AssistantClientState(entries, this.pending, this.serverState, current);
    }

    private AssistantClientState restoreHistory(Reception reception) {
        List<ChatEntry> entries = new ArrayList<>();
        for (AgentResult.Card card : reception.result().cards()) {
            if (!(card.data().value() instanceof ResultTypes.History(List<PlayerData.Message> messages))) continue;
            for (PlayerData.Message message : messages) {
                if (message.role().equals("user")) entries.add(new ChatEntry(true, message.text(), null));
                else {
                    try {
                        entries.add(new ChatEntry(false, "", CodecIO.parse(AgentResult.CODEC, message.text())));
                    } catch (RuntimeException error) {
                        entries.add(new ChatEntry(false, message.text(), null));
                    }
                }
            }
        }
        return new AssistantClientState(entries, this.pending, this.serverState, this.current);
    }

    private static ResultTypes.ClientState preferences(ResultTypes.ClientState previous, boolean history, boolean external) {
        return new ResultTypes.ClientState(history, external, previous.questAvailable(), previous.commands());
    }
}
