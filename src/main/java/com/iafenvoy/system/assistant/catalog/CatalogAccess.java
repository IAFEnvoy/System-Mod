package com.iafenvoy.system.assistant.catalog;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.assistant.MinecraftTools;
import com.iafenvoy.system.data.catalog.ConditionalEntry;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * World-dependent conditions only run on the game thread; callers keep storage I/O on workers.
 */
public final class CatalogAccess {
    private final MinecraftTools game;

    public CatalogAccess(MinecraftTools game) {
        this.game = game;
    }

    public <T> T read(ExecutionContext context, Function<ServerPlayer, T> query) {
        if (this.game.server().isSameThread())
            throw new IllegalStateException("Catalog reads must originate on a worker");
        CompletableFuture<T> future = this.game.onPlayer(context, query).toCompletableFuture();
        try {
            return future.get(context.remainingMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            future.cancel(false);
            Thread.currentThread().interrupt();
            throw new AgentException(AgentFailure.CANCELLED, error);
        } catch (TimeoutException error) {
            future.cancel(false);
            throw new AgentException(AgentFailure.EXPIRED, error);
        } catch (ExecutionException error) {
            throw new AgentException(AgentException.failure(error), error);
        }
    }

    public static boolean visible(ServerPlayer player, ConditionalEntry entry) {
        return entry.enabled() && test(player, entry.conditions().displayCondition());
    }

    /**
     * Call after checking visibility when building a preview.
     */
    public static boolean usable(ServerPlayer player, ConditionalEntry entry) {
        return entry.enabled() && test(player, entry.conditions().condition());
    }

    public static void require(ServerPlayer player, ConditionalEntry entry) {
        if (!visible(player, entry) || !usable(player, entry)) throw new AgentException(AgentFailure.CONDITION_NOT_MET);
    }

    private static boolean test(ServerPlayer player, EntityCondition condition) {
        try {
            return condition.test(player, FormulaContext.of(player));
        } catch (RuntimeException error) {
            SystemMod.LOGGER.warn("Catalog condition {} failed for player {}", condition.getClass().getName(), player.getUUID(), error);
            return false;
        }
    }
}
