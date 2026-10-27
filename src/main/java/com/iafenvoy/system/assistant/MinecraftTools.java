package com.iafenvoy.system.assistant;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.ExecutionContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Semaphore;
import java.util.function.Function;

public final class MinecraftTools {
    private final MinecraftServer server;
    private final GameTaskQueue queue;
    private final Semaphore mainQueue = new Semaphore(64);

    public MinecraftTools(MinecraftServer server, GameTaskQueue queue) {
        this.server = server;
        this.queue = queue;
    }

    public <T> CompletionStage<T> onPlayer(ExecutionContext context, Function<ServerPlayer, T> work) {
        // Game state is touched only inside the bounded tick queue, after rechecking task and player liveness.
        CompletableFuture<T> future = new CompletableFuture<>();
        if (!this.mainQueue.tryAcquire()) {
            future.completeExceptionally(new AgentException("BUSY"));
            return future;
        }
        boolean queued = this.queue.offer(() -> {
            try {
                context.check();
                ServerPlayer player = this.server.getPlayerList().getPlayer(context.player());
                if (player == null) throw new AgentException("PLAYER_OFFLINE");
                future.complete(work.apply(player));
            } catch (Exception e) {
                future.completeExceptionally(e);
            } catch (LinkageError e) {
                SystemMod.LOGGER.error("Assistant game adapter is incompatible", e);
                future.completeExceptionally(new AgentException("CAPABILITY_UNAVAILABLE"));
            } finally {
                this.mainQueue.release();
            }
        });
        if (!queued) {
            this.mainQueue.release();
            future.completeExceptionally(new AgentException("BUSY"));
        }
        return future;
    }
}
