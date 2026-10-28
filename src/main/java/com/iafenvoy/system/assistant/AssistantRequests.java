package com.iafenvoy.system.assistant;

import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.store.AgentStore;
import com.iafenvoy.system.network.ServerNetworkHandler;
import com.iafenvoy.system.util.TypeDispatch;
import net.minecraft.resources.Identifier;
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
    private final List<Identifier> modules;
    private final TypeDispatch<RequestContext, CompletionStage<AgentResponse>> requestHandlers = new TypeDispatch<>((_, _) -> {
        throw new AgentException("UNSUPPORTED");
    });
    private final Map<AgentRequest.Control, Function<RequestContext, CompletionStage<AgentResponse>>> controlHandlers = new EnumMap<>(AgentRequest.Control.class);

    AssistantRequests(MinecraftServer server, AgentEngine engine, AssistantPlayers players, List<Identifier> modules) {
        this.server = server;
        this.engine = engine;
        this.players = players;
        this.modules = List.copyOf(modules);
        this.registerRequests();
    }

    public void open(ServerPlayer player, AgentResponse.View view) {
        UUID connection = ServerNetworkHandler.connectionId(player);
        this.players.requireBound(player.getUUID()).whenComplete((result, error) -> this.server.execute(() -> {
            if (this.server.getPlayerList().getPlayer(player.getUUID()) != player || !connection.equals(ServerNetworkHandler.connectionId(player)))
                return;
            if (error == null) ServerNetworkHandler.send(player, new AgentResponse.Open(view));
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
        return work.thenApply(result -> new AgentResponse.Reply(context.requestId(), result));
    }

    private CompletionStage<AgentResponse> limited(RequestContext context, Supplier<CompletionStage<AgentResponse>> work) {
        if (!this.players.admitRequest(context.uuid()))
            return CompletableFuture.completedFuture(new AgentResponse.Reply(context.requestId(), AgentResult.error("RATE_LIMITED")));
        return work.get();
    }

    private <T extends AgentRequest> void registerRequest(Class<T> type, BiFunction<RequestContext, T, CompletionStage<AgentResponse>> handler) {
        this.requestHandlers.register(type, (context, request) -> this.limited(context, () -> handler.apply(context, request)));
    }

    private void registerControl(AgentRequest.Control control, Function<RequestContext, CompletionStage<AgentResponse>> handler) {
        this.controlHandlers.put(control, context -> this.limited(context, () -> handler.apply(context)));
    }

    private void registerRequests() {
        this.registerRequest(AgentRequest.Chat.class, (context, chat) -> answer(context, this.engine.submit(context.uuid(), context.requestId(), null, chat.text())));
        this.registerRequest(AgentRequest.Invoke.class, (context, invoke) -> answer(context, this.engine.submit(context.uuid(), context.requestId(), invoke.call(), invoke.text())));
        this.registerRequest(AgentRequest.Preferences.class, (context, preferences) -> answer(context, this.engine.preferences(context.uuid(), preferences.history(), preferences.external())));
        this.requestHandlers.register(AgentRequest.Control.class, (context, control) -> this.controlHandlers.get(control).apply(context));
        this.registerControl(AgentRequest.Control.STATE, context -> answer(context, this.state(context.uuid())));
        this.registerControl(AgentRequest.Control.HISTORY, context -> answer(context, this.engine.history(context.uuid())));
        this.registerControl(AgentRequest.Control.FORGET, context -> answer(context, this.engine.forget(context.uuid())));
        // Navigation must not consume the interval needed by the client's immediate state refresh.
        this.controlHandlers.put(AgentRequest.Control.OPEN, context -> this.players.requireBound(context.uuid())
                .thenApply(_ -> new AgentResponse.Open(AgentResponse.View.CURRENT, Optional.of(context.requestId()))));
    }

    private CompletionStage<AgentResult> state(UUID player) {
        AgentStore store = this.engine.playerStore(player);
        return this.engine.maintenance(() -> store.read(player, data -> {
            if (!data.bound) throw new AgentException("NOT_BOUND");
            return AgentResult.data("system.result.state",
                    ResultTypes.CLIENT_STATE.of(new ResultTypes.ClientState(data.saveHistory, data.externalModel,
                            this.modules,
                            this.engine.modules().commands().suggestions())));
        }));
    }
}
