package com.iafenvoy.system.assistant.economy;

import com.iafenvoy.saturn.runtime.resulting.Result;
import com.iafenvoy.saturn.util.math.Comparison;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.execution.AgentEngine;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.storage.AgentStore;
import com.iafenvoy.system.assistant.AssistantServer;
import com.iafenvoy.system.data.catalog.CurrencyDefinition;
import com.iafenvoy.system.data.output.WalletOutput;
import com.iafenvoy.system.module.ModuleData;
import com.iafenvoy.system.module.PlayerModule;
import com.iafenvoy.system.module.builtin.EconomySystemModule;
import com.iafenvoy.system.registry.SystemResourceKeys;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

/** One wallet service per world; durable edits run on workers, Cost's game-thread pass never waits for I/O. */
public final class Wallets implements PlayerModule {
    public static final Identifier QUERY = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "balance");
    private final AgentEngine engine;
    private final MinecraftServer server;
    private final Registry<CurrencyDefinition> currencies;

    public Wallets(AgentEngine engine, MinecraftServer server) {
        this.engine = engine;
        this.server = server;
        this.currencies = server.registryAccess().lookupOrThrow(SystemResourceKeys.CURRENCY);
    }

    public static Optional<Wallets> find(ServerPlayer player) {
        AssistantServer runtime = AssistantServer.get(player.level().getServer());
        return runtime == null ? Optional.empty() : runtime.moduleManager().runtime(Wallets.class);
    }

    public boolean available(ServerPlayer player) {
        return this.server.getPlayerList().getPlayer(player.getUUID()) == player && this.engine.available(player.getUUID(), QUERY);
    }

    public AgentStore store(ServerPlayer player) {
        return this.engine.playerStore(player.getUUID());
    }

    public WalletOutput query(ExecutionContext context) {
        context.ensureMemoryCurrent();
        return context.store().read(context.player(), data -> {
            var wallet = ModuleData.get(data, EconomySystemModule.STORAGE);
            return new WalletOutput(this.currencies.listElements()
                    .sorted(Comparator.<Holder.Reference<CurrencyDefinition>>comparingInt(holder -> holder.value().order())
                            .reversed().thenComparing(holder -> holder.key().identifier()))
                    .map(holder -> new WalletOutput.Entry(holder.key().identifier(), holder.value().name(), holder.value().description(),
                            holder.value().icon(), wallet.getBalance(holder.key().identifier()))).toList());
        });
    }

    public CompletionStage<Long> edit(ServerPlayer player, Identifier currency, long amount, boolean set) {
        if (!this.available(player)) throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
        if (this.currencies.getOptional(currency).isEmpty()) throw new AgentException(AgentFailure.UNKNOWN_CURRENCY);
        AgentStore store = this.store(player);
        return this.engine.maintenance(() -> store.update(player.getUUID(), data -> {
            if (!data.bound() || !this.engine.available(player.getUUID(), QUERY)) throw new AgentException(AgentFailure.NOT_BOUND);
            var wallet = ModuleData.get(data, EconomySystemModule.STORAGE);
            return set ? wallet.setBalance(currency, amount) : wallet.addBalance(currency, amount);
        }));
    }

    public CompletionStage<Long> balance(ServerPlayer player, Identifier currency) {
        if (!this.available(player)) throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
        if (this.currencies.getOptional(currency).isEmpty()) throw new AgentException(AgentFailure.UNKNOWN_CURRENCY);
        AgentStore store = this.store(player);
        return this.engine.maintenance(() -> store.read(player.getUUID(), data -> {
            if (!data.bound() || !this.engine.available(player.getUUID(), QUERY)) throw new AgentException(AgentFailure.NOT_BOUND);
            return ModuleData.get(data, EconomySystemModule.STORAGE).getBalance(currency);
        }));
    }

    public Result<Long> change(ServerPlayer player, AgentStore store, Identifier currency, long delta, boolean simulate) {
        if (!this.server.isSameThread() || !this.available(player)) return Result.failure(AgentFailure.CAPABILITY_UNAVAILABLE);
        if (this.currencies.getOptional(currency).isEmpty()) return Result.failure(AgentFailure.UNKNOWN_CURRENCY);
        AtomicReference<Result<Long>> answer = new AtomicReference<>(Result.failure(AgentFailure.BUSY));
        store.updateLoaded(player.getUUID(), data -> {
            if (!data.bound()) {
                answer.set(Result.failure(AgentFailure.NOT_BOUND));
                return false;
            }
            var wallet = ModuleData.get(data, EconomySystemModule.STORAGE);
            try {
                // updateLoaded supplies a detached draft: simulation discards it, a real pass publishes it atomically.
                answer.set(Result.success(wallet.addBalance(currency, delta)));
            } catch (AgentException error) {
                answer.set(Result.failure(error.failure()));
                return false;
            }
            return !simulate;
        });
        if (!simulate && answer.get().getFailure().isEmpty()) {
            AssistantServer runtime = AssistantServer.get(this.server);
            if (runtime != null) runtime.moduleManager().flush(player.getUUID());
        }
        return answer.get();
    }

    public boolean testBalance(ServerPlayer player, Identifier currency, Comparison comparison) {
        if (!this.server.isSameThread() || !this.available(player)) return false;
        try {
            return this.store(player).readLoaded(player.getUUID(), data -> data.bound()
                    && comparison.compare(ModuleData.get(data, EconomySystemModule.STORAGE).getBalance(currency))).orElse(false);
        } catch (AgentException error) {
            return false;
        }
    }
}
