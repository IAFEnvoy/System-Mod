package com.iafenvoy.system.agent;

import com.iafenvoy.system.agent.routing.RouteDecision;
import com.iafenvoy.system.agent.store.AgentStore;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.config.file.AgentConfig;
import com.iafenvoy.system.util.CodecIO;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interactive scheduling, cancellation and request replay; workflows execute through the engine.
 */
final class InteractiveExecutor implements AutoCloseable {
    private final AgentEngine engine;
    private final TaskExecutor interactive = new TaskExecutor("agent-chat", 4, 32);
    private final Map<UUID, String> owners = new ConcurrentHashMap<>();
    private final Map<UUID, ExecutionContext> active = new ConcurrentHashMap<>();
    private final Map<UUID, Long> generations = new ConcurrentHashMap<>();

    InteractiveExecutor(AgentEngine engine) {
        this.engine = engine;
    }

    public CompletionStage<AgentResult> submit(UUID player, String requestId, AgentAction input, String text) {
        if (this.engine.closed()) return CompletableFuture.completedFuture(AgentResult.error("CANCELLED"));
        try {
            UUID.fromString(requestId);
            if (text == null || text.codePointCount(0, text.length()) > 2000 || text.getBytes(StandardCharsets.UTF_8).length > 8192
                    || input != null && CodecIO.write(AgentAction.CODEC, input).length() > 4096)
                throw new AgentException("INVALID_INPUT");
        } catch (RuntimeException error) {
            return CompletableFuture.completedFuture(AgentResult.error("INVALID_INPUT"));
        }
        if (this.owners.putIfAbsent(player, requestId) != null)
            return CompletableFuture.completedFuture(AgentResult.error("BUSY"));
        long received = System.currentTimeMillis();
        long generation = this.generations.getOrDefault(player, 0L);
        long incarnation = this.engine.incarnation(player);
        AgentStore playerStore = this.engine.playerStore(player, incarnation);
        return this.interactive.submit(() -> {
                    if (System.currentTimeMillis() - received > 500) throw new AgentException("BUSY");
                    this.checkGeneration(player, generation);
                    return this.runInteractive(player, requestId, input, text, received, playerStore, incarnation, generation);
                }).handle((result, error) -> error == null ? result : AgentResult.error(AgentEngine.errorCode(error)))
                .whenComplete((result, error) -> {
                    this.active.remove(player);
                    this.owners.remove(player, requestId);
                });
    }

    private void checkGeneration(UUID player, long generation) {
        if (generation != this.generations.getOrDefault(player, 0L)) throw new AgentException("CANCELLED");
    }

    private AgentResult runInteractive(UUID player, String id, AgentAction input, String text, long received, AgentStore store, long incarnation, long generation) throws Exception {
        String moduleId = input == null ? "" : input.id().toString();
        // A retained request ID may replay its result only when its original input fingerprint matches.
        String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((moduleId + "\n" + (input == null ? "{}" : CodecIO.write(AgentAction.CODEC, input)) + "\n" + text).getBytes(StandardCharsets.UTF_8)));
        AgentResult previous = store.read(player, data -> {
            this.checkGeneration(player, generation);
            if (!data.bound()) throw new AgentException("NOT_BOUND");
            if (data.conversation.requestInputs.containsKey(id) && !fingerprint.equals(data.conversation.requestInputs.get(id)))
                throw new AgentException("INVALID_INPUT");
            return data.conversation.requests.get(id);
        });
        if (previous != null) return previous;
        long epoch = store.update(player, data -> {
            this.checkGeneration(player, generation);
            data.conversation.requestInputs.put(id, fingerprint);
            if (data.saveHistory && !text.isBlank())
                data.conversation.history.add(new PlayerData.Message(id, "user", received, text));
            return data.conversation.memoryEpoch;
        });
        AgentModule<?> direct = this.engine.module(player, ResourceLocation.tryParse(moduleId));
        ExecutionPolicy initial = moduleId.isBlank() || direct == null ? AgentConfig.INSTANCE.get().routingPolicy() : this.engine.policy(direct);
        ExecutionContext context = new ExecutionContext(this.engine, player, id, null, received, epoch, initial, store, incarnation);
        this.active.put(player, context);
        AgentResult result;
        try {
            this.checkGeneration(player, generation);
            RouteDecision decision = this.engine.router().route(context, input, text);
            moduleId = decision.moduleId();
            input = decision.input();
            context.routeSource(decision.source());
            context.ensureMemoryCurrent();
            if (moduleId.equals("clarify")) {
                result = this.engine.router().clarify(context);
            } else {
                result = this.engine.execute(context, input);
                if (result.isSuccess()) this.engine.router().remember(context, decision, text);
            }
        } catch (Exception error) {
            result = AgentResult.error(AgentEngine.errorCode(error));
        }
        AgentResult completed = result;
        store.update(player, data -> {
            if (generation != this.generations.getOrDefault(player, 0L)) return null;
            if (data.saveHistory && data.conversation.memoryEpoch == epoch) {
                data.conversation.requests.put(id, completed);
                if (!completed.silent())
                    data.conversation.history.add(new PlayerData.Message(id, "assistant", System.currentTimeMillis(), CodecIO.write(AgentResult.CODEC, completed)));
            } else data.conversation.requestInputs.remove(id);
            return null;
        });
        return completed;
    }

    public void cancel(UUID player) {
        this.generations.merge(player, 1L, Long::sum);
        ExecutionContext context = this.active.get(player);
        if (context != null) context.cancel();
    }

    @Override
    public void close() {
        this.active.values().forEach(ExecutionContext::cancel);
        this.interactive.close();
    }
}
