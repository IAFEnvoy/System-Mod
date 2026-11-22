package com.iafenvoy.system.assistant;

import com.iafenvoy.assistant.AgentResult;
import com.iafenvoy.assistant.action.AgentAction;
import com.iafenvoy.assistant.chat.AiStatus;
import com.iafenvoy.assistant.request.*;
import com.iafenvoy.assistant.response.AgentResponse;
import com.iafenvoy.assistant.response.CommandSuggestion;
import com.iafenvoy.assistant.response.CommandSuggestionsResponse;
import com.iafenvoy.assistant.storage.AgentStore;
import com.iafenvoy.assistant.util.TypeDispatch;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.SystemResults;
import com.iafenvoy.system.agent.execution.AgentEngine;
import com.iafenvoy.system.agent.request.CardActionRequest;
import com.iafenvoy.system.agent.response.HistoryResponse;
import com.iafenvoy.system.agent.response.OpenResponse;
import com.iafenvoy.system.agent.response.StateResponse;
import com.iafenvoy.system.assistant.item.ItemSearches;
import com.iafenvoy.system.data.ClientState;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.input.StarterGiftsInput;
import com.iafenvoy.system.data.item.ItemSearchBatch;
import com.iafenvoy.system.data.output.StarterGiftOutput;
import com.iafenvoy.system.data.store.PlayerData;
import com.iafenvoy.system.event.CardButtonEvent;
import com.iafenvoy.system.module.ModuleManager;
import com.iafenvoy.system.network.ServerNetworkHandler;
import com.iafenvoy.system.registry.SystemResponseTypes;
import com.iafenvoy.system.util.CardEventValue;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Registered request dispatch and navigation; asynchronous replies retain their original connection generation.
 */
public final class AssistantRequests {
    private final MinecraftServer server;
    private final AgentEngine engine;
    private final AssistantPlayers players;
    private final ModuleManager modules;
    private final ItemSearches itemSearches;
    private final TypeDispatch<RequestContext, CompletionStage<AgentResponse>> requestHandlers = new TypeDispatch<>((_, _) -> {
        throw new AgentException(AgentFailure.UNSUPPORTED);
    });
    private final Map<ControlRequest, Function<RequestContext, CompletionStage<AgentResponse>>> controlHandlers = new EnumMap<>(ControlRequest.class);

    AssistantRequests(MinecraftServer server, AgentEngine engine, AssistantPlayers players, ModuleManager modules, ItemSearches itemSearches) {
        this.server = server;
        this.engine = engine;
        this.players = players;
        this.modules = modules;
        this.itemSearches = itemSearches;
        this.registerRequests();
    }

    public void open(ServerPlayer player, OpenResponse.View view) {
        UUID connection = ServerNetworkHandler.connectionId(player);
        ServerNetworkHandler.CONNECTIONS.whenCurrent(player, connection, this.players.requireBound(player.getUUID()), (_, error) -> {
            if (error == null) ServerNetworkHandler.send(player, new OpenResponse(view));
            else ServerNetworkHandler.notice(player, SystemResults.error(AgentException.failure(error)));
        });
    }

    public void request(ServerPlayer player, String requestId, AgentRequest request, Optional<ItemSearchBatch> search) {
        this.players.refreshPlayer(player);
        if (search.isPresent() && !(request instanceof InvokeRequest || request instanceof CardActionRequest))
            throw new AgentException(AgentFailure.INVALID_INPUT);
        UUID connection = ServerNetworkHandler.connectionId(player);
        CompletionStage<AgentResponse> work = this.requestHandlers.apply(new RequestContext(player, requestId, search), request);
        ServerNetworkHandler.CONNECTIONS.whenCurrent(player, connection, work, (result, error) -> {
            if (error == null) ServerNetworkHandler.send(player, result);
            else ServerNetworkHandler.reply(player, requestId, SystemResults.error(AgentException.failure(error)));
        });
    }

    private record RequestContext(ServerPlayer player, String requestId, Optional<ItemSearchBatch> search) {
        UUID uuid() {
            return this.player.getUUID();
        }
    }

    private static CompletionStage<AgentResponse> answer(RequestContext context, CompletionStage<AgentResult<CardUpdate>> work) {
        return work.thenApply(result -> SystemResponseTypes.RESULTS.reply(context.requestId(), result));
    }

    private CompletionStage<AgentResponse> limited(RequestContext context, Supplier<CompletionStage<AgentResponse>> work) {
        if (!this.players.admitRequest(context.uuid()))
            return CompletableFuture.completedFuture(SystemResponseTypes.RESULTS.reply(context.requestId(), SystemResults.error(AgentFailure.RATE_LIMITED)));
        return work.get();
    }

    private <T extends AgentRequest> void registerRequest(Class<T> type, BiFunction<RequestContext, T, CompletionStage<AgentResponse>> handler) {
        this.requestHandlers.register(type, (context, request) -> this.limited(context, () -> handler.apply(context, request)));
    }

    private void registerControl(ControlRequest control, Function<RequestContext, CompletionStage<AgentResponse>> handler) {
        this.controlHandlers.put(control, context -> this.limited(context, () -> handler.apply(context)));
    }

    private void registerRequests() {
        // Completion is read-only: it neither starts Agent work nor consumes the chat request interval.
        this.requestHandlers.register(CommandSuggestionsRequest.class, (context, request) ->
                this.engine.modules().commands().suggest(request.text(), id -> this.engine.available(context.uuid(), id))
                        .thenApply(suggestions -> new CommandSuggestionsResponse(context.requestId(), request.text(),
                                suggestions.getList().stream().limit(256).map(suggestion -> new CommandSuggestion(
                                        suggestion.apply(request.text()), suggestion.getTooltip() == null ? "" : suggestion.getTooltip().getString())).toList())));
        this.registerRequest(ChatRequest.class, (context, chat) -> answer(context, this.engine.submit(context.uuid(), context.requestId(), null, chat.text())));
        this.registerRequest(InvokeRequest.class, (context, invoke) -> {
            AgentAction call = this.itemSearches.bind(context.player(), invoke.call(), context.search());
            return answer(context, this.engine.submit(context.uuid(), context.requestId(), call, invoke.text()));
        });
        this.registerRequest(CardActionRequest.class, (context, action) -> {
            ServerCards.Ticket ticket = ServerCards.begin(context.uuid(), action.source());
            return this.players.requireBound(context.uuid()).thenCompose(ignored -> answer(context, this.dispatchCardEvent(context, action)
                    .thenApply(result -> {
                        if (result.isSuccess() && action.value().equals(StarterGiftsInput.INSTANCE.id().toString())) {
                            boolean empty = result.cards().stream().flatMap(card -> card.data().stream())
                                    .filter(StarterGiftOutput.class::isInstance).map(StarterGiftOutput.class::cast).allMatch(gifts -> gifts.gifts().isEmpty());
                            result = result.withPresentation(result.messageKey(), result.text(), empty ? List.of() : result.cards(), true);
                        }
                        return ServerCards.finish(ticket, result);
                    })));
        });
        this.registerRequest(PreferencesRequest.class, (context, preferences) ->
                this.engine.preferences(context.uuid(), preferences.history(), preferences.mode()).thenCompose(result ->
                        result.isSuccess() ? this.state(context.uuid()).thenApply(state -> new StateResponse(context.requestId(), state))
                                : CompletableFuture.completedFuture(SystemResponseTypes.RESULTS.reply(context.requestId(), result))));
        this.requestHandlers.register(ControlRequest.class, (context, control) -> this.controlHandlers.get(control).apply(context));
        this.registerControl(ControlRequest.STATE, context -> this.state(context.uuid()).thenApply(state -> new StateResponse(context.requestId(), state)));
        this.registerControl(ControlRequest.HISTORY, context -> this.engine.history(context.uuid()).thenApply(history -> new HistoryResponse(context.requestId(), history)));
        this.registerControl(ControlRequest.FORGET, context -> answer(context, this.engine.forget(context.uuid())));
        // Navigation must not consume the interval needed by the client's immediate state refresh.
        this.controlHandlers.put(ControlRequest.OPEN, context -> this.players.requireBound(context.uuid())
                .thenApply(_ -> new OpenResponse(OpenResponse.View.CURRENT, Optional.of(context.requestId()))));
    }

    private CompletionStage<AgentResult<CardUpdate>> dispatchCardEvent(RequestContext context, CardActionRequest request) {
        CompletableFuture<AgentResult<CardUpdate>> result = new CompletableFuture<>();
        // Binding checks may finish on the storage executor; mod events always run on the game thread.
        this.server.execute(() -> {
            try {
                if (this.server.getPlayerList().getPlayer(context.uuid()) != context.player())
                    throw new AgentException(AgentFailure.CANCELLED);
                CardButtonEvent event = new CardButtonEvent(context.player(), request.source(), request.value());
                NeoForge.EVENT_BUS.post(event);
                CompletionStage<AgentResult<CardUpdate>> work = event.result();
                if (work == null) {
                    CardEventValue decoded;
                    try {
                        decoded = CardEventValue.parse(request.value());
                    } catch (IllegalArgumentException error) {
                        throw new AgentException(AgentFailure.INVALID_INPUT, error);
                    }
                    AgentAction call = this.engine.modules().button(decoded.action(), decoded.arguments());
                    call = this.itemSearches.bind(context.player(), call, context.search());
                    work = this.engine.submit(context.uuid(), context.requestId(), call, "");
                }
                work.whenComplete((reply, error) -> {
                    if (error == null) result.complete(reply);
                    else result.completeExceptionally(error);
                });
            } catch (RuntimeException error) {
                result.completeExceptionally(error);
            }
        });
        return result;
    }

    private CompletionStage<ClientState> state(UUID player) {
        AgentStore<PlayerData> store = this.engine.playerStore(player);
        return this.engine.maintenance(() -> store.read(player, data -> {
            if (!data.bound()) throw new AgentException(AgentFailure.NOT_BOUND);
            return new ClientState(data.systems.current(), data.saveHistory,
                    this.modules.enabled(player),
                    this.engine.modules().commands().suggestions(id -> this.engine.available(player, id)),
                    this.engine.requestTimeoutMillis(player), AiStatus.of(data.mode, this.engine.permissions(player)));
        }));
    }

    public void syncState(ServerPlayer player) {
        UUID connection = ServerNetworkHandler.connectionId(player);
        long incarnation = this.engine.incarnation(player.getUUID());
        ServerNetworkHandler.CONNECTIONS.whenCurrent(player, connection, this.state(player.getUUID()), (result, error) -> {
            if (error == null && this.engine.current(player.getUUID(), incarnation))
                ServerNetworkHandler.send(player, new StateResponse("", result));
        });
    }
}
