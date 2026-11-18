package com.iafenvoy.system.assistant.space;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.execution.AgentEngine;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.storage.AgentStore;
import com.iafenvoy.system.agent.routing.RouteSource;
import com.iafenvoy.system.assistant.MinecraftTools;
import com.iafenvoy.system.data.input.SpaceDepositInput;
import com.iafenvoy.system.data.module.SystemModule;
import com.iafenvoy.system.data.output.SpaceInventoryOutput;
import com.iafenvoy.system.data.space.SpaceDeposit;
import com.iafenvoy.system.data.store.PlayerData;
import com.iafenvoy.system.module.ModuleData;
import com.iafenvoy.system.module.PlayerModule;
import com.iafenvoy.system.module.builtin.SpaceSystemModule;
import com.iafenvoy.system.module.builtin.storage.SpaceDataStorage;
import com.iafenvoy.system.registry.SystemResourceKeys;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Server-issued slot snapshots and durable reservations guard inventory-to-space transfers. */
public final class SpaceDeposits implements PlayerModule {
    private record Quote(String id, Identifier owner, SystemModule module, ServerPlayer player, int slot,
                         ItemStack target, ItemStackTemplate snapshot, int maxStack, long incarnation, long expires) {
    }

    private static final class Settlement {
        final AgentStore store;
        final UUID player;
        final SpaceDeposit deposit;
        final AtomicBoolean touched = new AtomicBoolean(), saving = new AtomicBoolean();
        final CompletableFuture<SpaceDeposit> result = new CompletableFuture<>();
        volatile boolean ready;
        volatile long retryAt;
        Throwable error;

        Settlement(ExecutionContext context, SpaceDeposit deposit) {
            this.store = context.store();
            this.player = context.player();
            this.deposit = deposit;
        }
    }

    private final AgentEngine engine;
    private final MinecraftTools game;
    private final Map<UUID, Map<String, Quote>> quotes = new ConcurrentHashMap<>();
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Settlement> pending = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public SpaceDeposits(AgentEngine engine, MinecraftTools game) {
        this.engine = engine;
        this.game = game;
    }

    private Quote quote(ExecutionContext context, ServerPlayer player, Identifier owner, int slot) {
        ItemStack target = player.getInventory().getItem(slot);
        if (target.isEmpty()) throw new AgentException(AgentFailure.EMPTY_HAND);
        return new Quote(UUID.randomUUID().toString(), owner,
                player.registryAccess().lookupOrThrow(SystemResourceKeys.SYSTEM_MODULE).getValue(owner), player, slot,
                target, ItemStackTemplate.fromNonEmptyStack(target), target.getMaxStackSize(), this.engine.incarnation(context.player()), System.currentTimeMillis() + 120000);
    }

    public CompletionStage<SpaceInventoryOutput> inventory(ExecutionContext context, Identifier owner, int capacity) {
        return this.inventory(context, owner, capacity, Component.empty());
    }

    private CompletionStage<SpaceInventoryOutput> inventory(ExecutionContext context, Identifier owner, int capacity, Component feedback) {
        long used = context.store().read(context.player(), data -> ModuleData.get(data, SpaceSystemModule.STORAGE).depositUsage());
        return this.game.onPlayer(context, player -> {
            Map<String, Quote> issued = new ConcurrentHashMap<>();
            List<SpaceInventoryOutput.Entry> entries = new ArrayList<>();
            for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
                if (player.getInventory().getItem(slot).isEmpty()) continue;
                Quote quote = this.quote(context, player, owner, slot);
                issued.put(quote.id, quote);
                entries.add(new SpaceInventoryOutput.Entry(quote.id, slot, quote.snapshot));
            }
            this.quotes.put(context.player(), issued);
            return new SpaceInventoryOutput(entries, used, capacity, feedback);
        });
    }

    private void validate(ExecutionContext context, ServerPlayer player, Quote quote) {
        if (this.closed || player != quote.player || quote.expires < System.currentTimeMillis()
                || !this.engine.current(context.player(), quote.incarnation) || !this.engine.moduleEnabled(context.player(), quote.owner)
                || player.registryAccess().lookupOrThrow(SystemResourceKeys.SYSTEM_MODULE).getValue(quote.owner) != quote.module
                || player.getInventory().getItem(quote.slot) != quote.target || !ItemStack.matches(quote.target, quote.snapshot))
            throw new AgentException(AgentFailure.STALE_REFERENCE);
    }

    public CompletionStage<SpaceInventoryOutput> deposit(ExecutionContext context, SpaceDepositInput input, Identifier owner, int capacity) {
        if (context.routeSource() != RouteSource.STRUCTURED && context.routeSource() != RouteSource.COMMAND)
            return this.inventory(context, owner, capacity, Component.translatable("system.space.deposit_confirm"));
        context.requirePlayerRequest(input);
        if (capacity == 0) throw new AgentException(AgentFailure.SPACE_DEPOSIT_FULL);
        SpaceDeposit previous = context.store().read(context.player(), data -> ModuleData.get(data, SpaceSystemModule.STORAGE).deposits().get(input.reference()));
        if (previous != null) {
            if (previous.state() != SpaceDeposit.State.COMPLETED)
                throw new AgentException(previous.state() == SpaceDeposit.State.FAILED ? AgentFailure.STALE_REFERENCE : AgentFailure.EFFECT_UNKNOWN);
            return this.inventory(context, owner, capacity, Component.translatable("system.space.deposited", previous.item().create().getHoverName(), previous.amount()))
                    .thenApply(output -> output.withDeposit(previous.item()));
        }
        if (!this.active.add(context.player())) throw new AgentException(AgentFailure.BUSY);
        return this.game.onPlayer(context, player -> {
            Quote quote = input.reference().isEmpty() ? this.quote(context, player, owner, player.getInventory().getSelectedSlot())
                    : this.quotes.getOrDefault(context.player(), Map.of()).get(input.reference());
            if (quote == null || !quote.owner.equals(owner)) throw new AgentException(AgentFailure.STALE_REFERENCE);
            this.validate(context, player, quote);
            return quote;
        }).thenCompose(quote -> this.engine.maintenance(() -> {
            context.check();
            int amount = input.stack() ? Math.min(quote.snapshot.count(), quote.maxStack) : input.amount();
            if (amount < 1 || amount > quote.snapshot.count()) throw new AgentException(AgentFailure.INSUFFICIENT_COST);
            SpaceDeposit order = new SpaceDeposit(quote.id, quote.snapshot, quote.slot, amount, SpaceDeposit.State.PREPARED, System.currentTimeMillis(), "", "");
            Settlement settlement = new Settlement(context, order);
            this.pending.put(context.player(), settlement);
            try {
                context.store().update(context.player(), data -> {
                    context.check();
                    ModuleData.get(data, SpaceSystemModule.STORAGE).beginDeposit(order, capacity);
                    return null;
                });
            } catch (RuntimeException error) {
                this.pending.remove(context.player(), settlement);
                throw error;
            }
            CompletionStage<Void> execution;
            try {
                context.store().update(context.player(), data -> {
                    context.check();
                    ModuleData.get(data, SpaceSystemModule.STORAGE).executingDeposit(order.id());
                    return null;
                });
                execution = this.game.onPlayer(context, player -> {
                    this.validate(context, player, quote);
                    if (input.reference().isEmpty() && player.getInventory().getSelectedSlot() != quote.slot)
                        throw new AgentException(AgentFailure.STALE_REFERENCE);
                    context.check();
                    Map<String, Quote> issued = this.quotes.get(context.player());
                    if (issued != null) issued.remove(quote.id);
                    settlement.touched.set(true);
                    quote.target.shrink(amount);
                    player.getInventory().setChanged();
                    player.containerMenu.broadcastChanges();
                    return null;
                });
            } catch (RuntimeException error) {
                execution = CompletableFuture.failedFuture(error);
            }
            execution.whenComplete((_, error) -> {
                settlement.error = error;
                settlement.ready = true;
                this.settle(settlement);
            });
            return settlement.result;
        }).thenCompose(stage -> stage)).thenCompose(order -> this.engine.maintenance(() -> this.inventory(context, owner, capacity,
                Component.translatable("system.space.deposited", order.item().create().getHoverName(), order.amount()))).thenCompose(stage -> stage)
                .thenApply(output -> output.withDeposit(order.item())))
                .whenComplete((_, _) -> {
                    if (!this.pending.containsKey(context.player())) this.active.remove(context.player());
                });
    }

    private void settle(Settlement settlement) {
        if (this.closed || !settlement.ready || !settlement.saving.compareAndSet(false, true)) return;
        this.engine.maintenance(() -> settlement.store.update(settlement.player, data -> {
            SpaceDataStorage storage = ModuleData.get(data, SpaceSystemModule.STORAGE);
            storage.finishDeposit(settlement.deposit.id(), settlement.error == null, settlement.touched.get());
            return storage.deposits().get(settlement.deposit.id());
        })).whenComplete((order, error) -> {
            if (error == null) {
                this.pending.remove(settlement.player, settlement);
                this.active.remove(settlement.player);
                if (settlement.error == null) settlement.result.complete(order);
                else settlement.result.completeExceptionally(new AgentException(settlement.touched.get() ? AgentFailure.EFFECT_UNKNOWN
                        : AgentException.failure(settlement.error), settlement.error));
            } else if (AgentException.failure(error) == AgentFailure.CANCELLED) {
                this.pending.remove(settlement.player, settlement);
                this.active.remove(settlement.player);
                settlement.result.completeExceptionally(error);
            } else {
                SystemMod.LOGGER.warn("Space deposit settlement failed; retrying: player={}, deposit={}", settlement.player, settlement.deposit.id(), error);
                settlement.retryAt = System.currentTimeMillis() + 5000;
                settlement.saving.set(false);
                settlement.result.completeExceptionally(new AgentException(AgentFailure.EFFECT_UNKNOWN, error));
            }
        });
    }

    private void recover(UUID player, PlayerData data) {
        Settlement settlement = this.pending.get(player);
        ModuleData.get(data, SpaceSystemModule.STORAGE).recoverDeposits(settlement == null ? Set.of() : Set.of(settlement.deposit.id()));
    }

    public CompletionStage<List<SpaceDeposit>> orders(UUID player) {
        AgentStore store = this.engine.playerStore(player);
        return this.engine.maintenance(() -> store.update(player, data -> {
            this.recover(player, data);
            return ModuleData.get(data, SpaceSystemModule.STORAGE).deposits().values().stream()
                    .sorted(Comparator.comparingLong(SpaceDeposit::created).reversed()).toList();
        }));
    }

    public CompletionStage<SpaceDeposit> resolve(UUID player, String id, boolean consumed, String reviewer, String reason) {
        if (reason.isBlank() || reason.length() > 512) throw new AgentException(AgentFailure.INVALID_INPUT);
        if (!this.active.add(player)) throw new AgentException(AgentFailure.BUSY);
        AgentStore store = this.engine.playerStore(player);
        return this.engine.maintenance(() -> store.update(player, data -> {
            this.recover(player, data);
            return ModuleData.get(data, SpaceSystemModule.STORAGE).reviewDeposit(id, consumed, reviewer, reason);
        })).thenApply(order -> {
            SystemMod.LOGGER.warn("Space deposit reviewed: player={}, deposit={}, consumed={}, reviewer={}, reason={}", player, id, consumed, reviewer, reason);
            return order;
        }).whenComplete((_, _) -> this.active.remove(player));
    }

    @Override
    public Session login(ServerPlayer player) {
        UUID id = player.getUUID();
        return new Session() {
            @Override
            public void initialize(PlayerData data) {
                SpaceDeposits.this.recover(id, data);
            }

            @Override
            public void close() {
                SpaceDeposits.this.quotes.remove(id);
            }
        };
    }

    @Override
    public void tick() {
        long now = System.currentTimeMillis();
        this.quotes.values().forEach(map -> map.values().removeIf(quote -> quote.expires < now));
        this.pending.values().stream().filter(settlement -> settlement.retryAt <= now).forEach(this::settle);
    }

    @Override
    public void close() {
        this.closed = true;
        this.quotes.clear();
        this.pending.values().forEach(settlement -> settlement.result.completeExceptionally(new AgentException(AgentFailure.CANCELLED)));
        this.pending.clear();
        this.active.clear();
    }
}
