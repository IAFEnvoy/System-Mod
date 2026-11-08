package com.iafenvoy.system.assistant;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.execution.AgentEngine;
import com.iafenvoy.system.agent.module.AgentAction;
import com.iafenvoy.system.agent.request.*;
import com.iafenvoy.system.agent.response.*;
import com.iafenvoy.system.agent.storage.AgentStore;
import com.iafenvoy.system.data.AiStatus;
import com.iafenvoy.system.data.ClientState;
import com.iafenvoy.system.data.input.StarterGiftsInput;
import com.iafenvoy.system.data.output.StarterGiftOutput;
import com.iafenvoy.system.event.CardButtonEvent;
import com.iafenvoy.system.module.ModuleManager;
import com.iafenvoy.system.network.ServerNetworkHandler;
import com.iafenvoy.system.util.CardEventValue;
import com.iafenvoy.system.util.TypeDispatch;
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
    private final TypeDispatch<RequestContext, CompletionStage<AgentResponse>> requestHandlers = new TypeDispatch<>((_, _) -> {
        throw new AgentException(AgentFailure.UNSUPPORTED);
    });
    private final Map<ControlRequest, Function<RequestContext, CompletionStage<AgentResponse>>> controlHandlers = new EnumMap<>(ControlRequest.class);

    AssistantRequests(MinecraftServer server, AgentEngine engine, AssistantPlayers players, ModuleManager modules) {
        this.server = server;
        this.engine = engine;
        this.players = players;
        this.modules = modules;
        this.registerRequests();
    }

    public void open(ServerPlayer player, OpenResponse.View view) {
        UUID connection = ServerNetworkHandler.connectionId(player);
        this.players.requireBound(player.getUUID()).whenComplete((_, error) -> this.server.execute(() -> {
            if (this.server.getPlayerList().getPlayer(player.getUUID()) != player || !connection.equals(ServerNetworkHandler.connectionId(player)))
                return;
            if (error == null) ServerNetworkHandler.send(player, new OpenResponse(view));
            else ServerNetworkHandler.notice(player, AgentResult.error(AgentException.failure(error)));
        }));
    }

    public void request(ServerPlayer player, String requestId, AgentRequest request) {
        this.players.refreshPlayer(player);
        UUID uuid = player.getUUID();
        UUID connection = ServerNetworkHandler.connectionId(player);
        CompletionStage<AgentResponse> work = this.requestHandlers.apply(new RequestContext(player, requestId), request);
        work.whenComplete((result, error) -> this.server.execute(() -> {
            ServerPlayer online = this.server.getPlayerList().getPlayer(uuid);
            if (online != null && connection.equals(ServerNetworkHandler.connectionId(online))) {
                if (error == null) ServerNetworkHandler.send(online, result);
                else ServerNetworkHandler.reply(online, requestId, AgentResult.error(AgentException.failure(error)));
            }
        }));
    }

    private record RequestContext(ServerPlayer player, String requestId) {
        UUID uuid() {
            return this.player.getUUID();
        }
    }

    private static CompletionStage<AgentResponse> answer(RequestContext context, CompletionStage<AgentResult> work) {
        return work.thenApply(result -> new ReplyResponse(context.requestId(), result));
    }

    private CompletionStage<AgentResponse> limited(RequestContext context, Supplier<CompletionStage<AgentResponse>> work) {
        if (!this.players.admitRequest(context.uuid()))
            return CompletableFuture.completedFuture(new ReplyResponse(context.requestId(), AgentResult.error(AgentFailure.RATE_LIMITED)));
        return work.get();
    }

    private <T extends AgentRequest> void registerRequest(Class<T> type, BiFunction<RequestContext, T, CompletionStage<AgentResponse>> handler) {
        this.requestHandlers.register(type, (context, request) -> this.limited(context, () -> handler.apply(context, request)));
    }

    private void registerControl(ControlRequest control, Function<RequestContext, CompletionStage<AgentResponse>> handler) {
        this.controlHandlers.put(control, context -> this.limited(context, () -> handler.apply(context)));
    }

    private void registerRequests() {
        this.registerRequest(ChatRequest.class, (context, chat) -> answer(context, this.engine.submit(context.uuid(), context.requestId(), null, chat.text())));
        this.registerRequest(InvokeRequest.class, (context, invoke) -> answer(context, this.engine.submit(context.uuid(), context.requestId(), invoke.call(), invoke.text())));
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
                                : CompletableFuture.completedFuture(new ReplyResponse(context.requestId(), result))));
        this.requestHandlers.register(ControlRequest.class, (context, control) -> this.controlHandlers.get(control).apply(context));
        this.registerControl(ControlRequest.STATE, context -> this.state(context.uuid()).thenApply(state -> new StateResponse(context.requestId(), state)));
        this.registerControl(ControlRequest.HISTORY, context -> this.engine.history(context.uuid()).thenApply(history -> new HistoryResponse(context.requestId(), history)));
        this.registerControl(ControlRequest.FORGET, context -> answer(context, this.engine.forget(context.uuid())));
        // Navigation must not consume the interval needed by the client's immediate state refresh.
        this.controlHandlers.put(ControlRequest.OPEN, context -> this.players.requireBound(context.uuid())
                .thenApply(_ -> new OpenResponse(OpenResponse.View.CURRENT, Optional.of(context.requestId()))));
    }

    private CompletionStage<AgentResult> dispatchCardEvent(RequestContext context, CardActionRequest request) {
        CompletableFuture<AgentResult> result = new CompletableFuture<>();
        // Binding checks may finish on the storage executor; mod events always run on the game thread.
        this.server.execute(() -> {
            try {
                if (this.server.getPlayerList().getPlayer(context.uuid()) != context.player())
                    throw new AgentException(AgentFailure.CANCELLED);
                CardButtonEvent event = new CardButtonEvent(context.player(), request.source(), request.value());
                NeoForge.EVENT_BUS.post(event);
                CompletionStage<AgentResult> work = event.result();
                if (work == null) {
                    CardEventValue decoded;
                    try {
                        decoded = CardEventValue.parse(request.value());
                    } catch (IllegalArgumentException error) {
                        throw new AgentException(AgentFailure.INVALID_INPUT, error);
                    }
                    AgentAction call = this.engine.modules().button(decoded.action(), decoded.arguments());
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
        AgentStore store = this.engine.playerStore(player);
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
        this.state(player.getUUID()).whenComplete((result, error) -> this.server.execute(() -> {
            if (error == null && this.server.getPlayerList().getPlayer(player.getUUID()) == player
                    && connection.equals(ServerNetworkHandler.connectionId(player)) && this.engine.current(player.getUUID(), incarnation))
                ServerNetworkHandler.send(player, new StateResponse("", result));
        }));
    }
}
