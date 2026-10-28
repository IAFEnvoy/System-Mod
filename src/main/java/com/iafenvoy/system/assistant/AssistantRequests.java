package com.iafenvoy.system.assistant;

import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.request.ChatRequest;
import com.iafenvoy.system.agent.request.ControlRequest;
import com.iafenvoy.system.agent.request.InvokeRequest;
import com.iafenvoy.system.agent.request.PreferencesRequest;
import com.iafenvoy.system.agent.response.OpenResponse;
import com.iafenvoy.system.agent.response.ReplyResponse;
import com.iafenvoy.system.agent.store.AgentStore;
import com.iafenvoy.system.module.ModuleManager;
import com.iafenvoy.system.network.ServerNetworkHandler;
import com.iafenvoy.system.util.TypeDispatch;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

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
        throw new AgentException("UNSUPPORTED");
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
        this.players.requireBound(player.getUUID()).whenComplete((result, error) -> this.server.execute(() -> {
            if (this.server.getPlayerList().getPlayer(player.getUUID()) != player || !connection.equals(ServerNetworkHandler.connectionId(player)))
                return;
            if (error == null) ServerNetworkHandler.send(player, new OpenResponse(view));
            else ServerNetworkHandler.notice(player, AgentResult.error(AgentEngine.errorCode(error)));
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
                else ServerNetworkHandler.reply(online, requestId, AgentResult.error(AgentEngine.errorCode(error)));
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
            return CompletableFuture.completedFuture(new ReplyResponse(context.requestId(), AgentResult.error("RATE_LIMITED")));
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
        this.registerRequest(PreferencesRequest.class, (context, preferences) -> answer(context, this.engine.preferences(context.uuid(), preferences.history(), preferences.external())));
        this.requestHandlers.register(ControlRequest.class, (context, control) -> this.controlHandlers.get(control).apply(context));
        this.registerControl(ControlRequest.STATE, context -> answer(context, this.state(context.uuid())));
        this.registerControl(ControlRequest.HISTORY, context -> answer(context, this.engine.history(context.uuid())));
        this.registerControl(ControlRequest.FORGET, context -> answer(context, this.engine.forget(context.uuid())));
        // Navigation must not consume the interval needed by the client's immediate state refresh.
        this.controlHandlers.put(ControlRequest.OPEN, context -> this.players.requireBound(context.uuid())
                .thenApply(_ -> new OpenResponse(OpenResponse.View.CURRENT, Optional.of(context.requestId()))));
    }

    private CompletionStage<AgentResult> state(UUID player) {
        AgentStore store = this.engine.playerStore(player);
        return this.engine.maintenance(() -> store.read(player, data -> {
            if (!data.bound) throw new AgentException("NOT_BOUND");
            return AgentResult.data("system.result.state",
                    new ResultTypes.ClientState(data.saveHistory, data.externalModel,
                            this.modules.enabled(player),
                            this.engine.modules().commands().suggestions(id -> this.engine.available(player, id))));
        }));
    }

    public void syncState(ServerPlayer player) {
        UUID connection = ServerNetworkHandler.connectionId(player);
        long incarnation = this.engine.incarnation(player.getUUID());
        this.state(player.getUUID()).whenComplete((result, error) -> this.server.execute(() -> {
            if (error == null && this.server.getPlayerList().getPlayer(player.getUUID()) == player
                    && connection.equals(ServerNetworkHandler.connectionId(player)) && this.engine.current(player.getUUID(), incarnation))
                ServerNetworkHandler.notice(player, result);
        }));
    }
}
