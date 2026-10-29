package com.iafenvoy.system.assistant;

import com.iafenvoy.system.agent.AgentRequest;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.request.ControlRequest;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.card.CardRef;
import com.iafenvoy.system.card.CardUpdate;
import com.iafenvoy.system.card.client.ClientCards;
import com.iafenvoy.system.util.CodecIO;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Immutable connection state; visual cards, history and nonvisual server state have separate paths.
 */
public record AssistantClientState(List<ChatEntry> entries, Map<String, Pending> pending,
                                   ResultTypes.ClientState serverState, ChatEntry current) {
    public static final AssistantClientState EMPTY = new AssistantClientState(List.of(), Map.of(),
            new ResultTypes.ClientState(Optional.empty(), true, true, List.of(), List.of(), 60000), null);

    public record ChatEntry(boolean player, String text, AgentResult result) {
    }

    public record Pending(long deadline, Object owner, Optional<CardRef> notification) {
    }

    public AssistantClientState {
        entries = List.copyOf(entries.subList(Math.max(0, entries.size() - 64), entries.size()));
        pending = Map.copyOf(pending);
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

    public boolean hasModule(ResourceLocation id) {
        return this.serverState.modules().contains(id);
    }

    public List<String> commands() {
        return this.serverState.commands();
    }

    public List<ClientCards.Choice> currentActions() {
        return this.current == null || this.current.result() == null ? List.of() : ClientCards.choices(this.current.result());
    }

    AssistantClientState local(AgentResult result) {
        return this.append(result, false, true);
    }

    AssistantClientState requested(String id, AgentRequest request, Object owner, Optional<CardRef> notification) {
        List<ChatEntry> entries = new ArrayList<>(this.entries);
        if (!request.text().isBlank()) entries.add(new ChatEntry(true, request.text(), null));
        Map<String, Pending> pending = new HashMap<>(this.pending);
        pending.put(id, new Pending(System.currentTimeMillis() + this.serverState.requestTimeoutMillis(), owner, notification));
        return new AssistantClientState(entries, pending, this.serverState,
                request == ControlRequest.STATE || request == ControlRequest.HISTORY || request.text().isBlank()
                        ? this.current : new ChatEntry(false, request.text(), null));
    }

    AssistantClientState complete(String id) {
        if (!this.pending.containsKey(id)) return this;
        Map<String, Pending> pending = new HashMap<>(this.pending);
        pending.remove(id);
        return new AssistantClientState(this.entries, pending, this.serverState, this.current);
    }

    AssistantClientState withoutPending() {
        return new AssistantClientState(this.entries, Map.of(), this.serverState, this.current);
    }

    AssistantClientState synchronizedState(String id, ResultTypes.ClientState value) {
        return new AssistantClientState(this.entries, this.complete(id).pending, value, this.current);
    }

    AssistantClientState updated(String id) {
        List<ChatEntry> entries = this.entries.stream().map(AssistantClientState::latest).toList();
        return new AssistantClientState(entries, this.complete(id).pending, this.serverState, visible(this.current));
    }

    private static ChatEntry visible(ChatEntry entry) {
        if (entry == null || entry.result == null) return entry;
        List<CardUpdate> cards = entry.result.cards().stream().map(ClientCards::latest).filter(card -> ClientCards.active(card.ref())).toList();
        return cards.isEmpty() ? null : new ChatEntry(entry.player, entry.text,
                entry.result.withPresentation(entry.result.messageKey(), entry.result.text(), cards, entry.result.silent()));
    }

    private static ChatEntry latest(ChatEntry entry) {
        return entry == null || entry.result == null ? entry : new ChatEntry(entry.player, entry.text, ClientCards.latest(entry.result));
    }

    AssistantClientState receive(String id, AgentResult result, boolean notification, boolean select) {
        AssistantClientState completed = this.updated(id);
        if (result.silent() || result.cards().stream().noneMatch(card -> card.mode() == CardUpdate.Mode.SHOW))
            return completed;
        if (result.messageKey().equals("system.result.forgotten"))
            completed = new AssistantClientState(List.of(), completed.pending, completed.serverState, completed.current);
        return completed.append(result, notification, select);
    }

    private AssistantClientState append(AgentResult result, boolean notification, boolean select) {
        List<ChatEntry> entries = new ArrayList<>(this.entries);
        ChatEntry entry = new ChatEntry(false, "", result);
        entries.add(entry);
        ChatEntry current = !notification && select ? new ChatEntry(false, this.current == null ? "" : this.current.text, result) : this.current;
        return new AssistantClientState(entries, this.pending, this.serverState, current);
    }

    AssistantClientState history(String id, ResultTypes.History history) {
        List<ChatEntry> entries = new ArrayList<>();
        for (PlayerData.Message message : history.messages()) {
            if (message.role().equals("user")) entries.add(new ChatEntry(true, message.text(), null));
            else try {
                AgentResult result = CodecIO.parse(AgentResult.CODEC, message.text());
                result.cards().forEach(card -> ClientCards.apply(card, true));
                if (!result.silent()) entries.add(new ChatEntry(false, "", ClientCards.latest(result)));
            } catch (RuntimeException error) {
                entries.add(new ChatEntry(false, message.text(), null));
            }
        }
        // Live notices received while history was in flight keep their active snapshots and order.
        Set<UUID> loaded = new HashSet<>();
        entries.stream().filter(e -> e.result != null).flatMap(e -> e.result.cards().stream()).forEach(c -> loaded.add(c.ref().id()));
        for (ChatEntry entry : this.entries)
            if (entry.result != null && entry.result.cards().stream().anyMatch(c -> !loaded.contains(c.ref().id())))
                entries.add(entry);
        return new AssistantClientState(entries, this.complete(id).pending, this.serverState, this.current);
    }
}
