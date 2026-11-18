package com.iafenvoy.system.assistant.workshop;

import com.iafenvoy.evaluate.api.ItemValues;
import com.iafenvoy.saturn.data.cost.context.CostContext;
import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.execution.AgentEngine;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.storage.AgentStore;
import com.iafenvoy.system.assistant.MinecraftTools;
import com.iafenvoy.system.assistant.catalog.CatalogAccess;
import com.iafenvoy.system.assistant.commerce.CommerceCosts;
import com.iafenvoy.system.assistant.progress.PlayerFacts;
import com.iafenvoy.system.assistant.space.SpaceTransfer;
import com.iafenvoy.system.data.catalog.SalvageNumbers;
import com.iafenvoy.system.data.catalog.SalvageRule;
import com.iafenvoy.system.data.input.SalvageConfirmInput;
import com.iafenvoy.system.data.module.SystemModule;
import com.iafenvoy.system.data.output.SalvageHistoryOutput;
import com.iafenvoy.system.data.output.SalvageQuoteOutput;
import com.iafenvoy.system.data.output.SalvageReceiptOutput;
import com.iafenvoy.system.data.progress.PlayerFact;
import com.iafenvoy.system.data.space.ItemSpaceContent;
import com.iafenvoy.system.data.space.SpaceContent;
import com.iafenvoy.system.data.space.SpaceContentType;
import com.iafenvoy.system.data.space.SpaceStack;
import com.iafenvoy.system.data.store.Delivery;
import com.iafenvoy.system.data.store.PlayerData;
import com.iafenvoy.system.data.store.Reward;
import com.iafenvoy.system.data.store.SalvageOrder;
import com.iafenvoy.system.event.SalvageCheckEvent;
import com.iafenvoy.system.module.ModuleData;
import com.iafenvoy.system.module.PlayerModule;
import com.iafenvoy.system.module.builtin.SpaceSystemModule;
import com.iafenvoy.system.module.builtin.WorkshopSystemModule;
import com.iafenvoy.system.module.builtin.storage.SpaceDataStorage;
import com.iafenvoy.system.module.builtin.storage.WorkshopDataStorage;
import com.iafenvoy.system.registry.SystemResourceKeys;
import com.iafenvoy.system.util.CodecIO;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Durable destruction orders are separate from commerce; only final settlement may be retried.
 */
public final class Salvage implements PlayerModule {
    public static final Identifier FACT = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "salvaged");
    private final AgentEngine engine;
    private final MinecraftTools game;
    private final Map<UUID, Quote> quotes = new ConcurrentHashMap<>();
    private final Set<String> active = ConcurrentHashMap.newKeySet();
    private final Map<String, Settlement> pending = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public Salvage(AgentEngine engine, MinecraftTools game) {
        this.engine = engine;
        this.game = game;
        NeoForge.EVENT_BUS.register(this);
    }

    private record Quote(String id, Identifier owner, WorkshopSystemModule settings, SystemModule module,
                         Registry<SalvageRule> registry, Identifier ruleId, SalvageRule rule, ServerPlayer player,
                         int slot, ItemStack target, ItemStackTemplate input, CommerceCosts.Prepared payment,
                         Reward reward, long incarnation, long expires) {
    }

    public CompletionStage<SalvageQuoteOutput> quote(ExecutionContext context, Identifier owner, WorkshopSystemModule settings) {
        return this.game.onPlayer(context, player -> {
            this.quotes.remove(context.player());
            ItemStack stack = player.getMainHandItem();
            checkInput(stack);
            Registry<SalvageRule> registry = player.registryAccess().lookupOrThrow(SystemResourceKeys.SALVAGE_RULE);
            Holder.Reference<SalvageRule> selected = select(player, settings, stack);
            SalvageRule rule = selected.value();
            CatalogAccess.require(player, rule);
            Identifier ruleId = selected.key().identifier();
            checkEvent(player, ruleId, stack);
            int slot = player.getInventory().getSelectedSlot();
            ItemStackTemplate input = ItemStackTemplate.fromNonEmptyStack(stack);
            CommerceCosts.Prepared payment = CommerceCosts.prepare(rule.costs(), player, CostContext.of(player, "system:salvage")
                    .with("system:source", owner).withItemTarget(new WorkshopInventory(player.getInventory(), slot)));
            if (payment.isFailure()) throw new AgentException(payment.failure().orElseThrow());
            Reward reward = outputs(player, rule, stack);
            validateReward(player, reward);
            Quote quote = new Quote(UUID.randomUUID().toString(), owner, settings,
                    player.registryAccess().lookupOrThrow(SystemResourceKeys.SYSTEM_MODULE).getValue(owner), registry,
                    ruleId, rule, player, slot, stack, input, payment, reward, this.engine.incarnation(context.player()), System.currentTimeMillis() + 120000);
            this.validate(context, player, quote);
            this.quotes.put(context.player(), quote);
            return new SalvageQuoteOutput(quote.id(), ruleId, rule.label(), input, payment.getData(), reward);
        });
    }

    private static Holder.Reference<SalvageRule> select(ServerPlayer player, WorkshopSystemModule settings, ItemStack stack) {
        return player.registryAccess().lookupOrThrow(SystemResourceKeys.SALVAGE_RULE).listElements()
                .filter(holder -> settings.allows(player.registryAccess(), holder.key().identifier()))
                .filter(holder -> CatalogAccess.visible(player, holder.value()) && holder.value().input().test(stack)).min(Comparator.<Holder.Reference<SalvageRule>>comparingInt(holder -> holder.value().priority()).reversed()
                        .thenComparing(holder -> holder.key().identifier().toString())).orElseThrow(() -> new AgentException(AgentFailure.SALVAGE_NO_RULE));
    }

    private static void checkInput(ItemStack stack) {
        if (stack.isEmpty()) throw new AgentException(AgentFailure.EMPTY_HAND);
        if (stack.getCount() != 1) throw new AgentException(AgentFailure.SALVAGE_SINGLE_ITEM);
        if (stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream().findAny().isPresent()
                || !stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).isEmpty()
                || stack.has(DataComponents.CHARGED_PROJECTILES) && !stack.get(DataComponents.CHARGED_PROJECTILES).isEmpty())
            throw new AgentException(AgentFailure.SALVAGE_CONTAINER);
    }

    private static void checkEvent(ServerPlayer player, Identifier rule, ItemStack input) {
        SalvageCheckEvent event = NeoForge.EVENT_BUS.post(new SalvageCheckEvent(player, rule, input));
        event.failure().ifPresent(failure -> {
            throw new AgentException(failure);
        });
    }

    private static Reward outputs(ServerPlayer player, SalvageRule rule, ItemStack stack) {
        int max = stack.isDamageableItem() ? stack.getMaxDamage() : 0;
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        Map<String, Double> variables = new HashMap<>();
        variables.put("salvage_damage", (double) (max == 0 ? 0 : stack.getDamageValue()));
        variables.put("salvage_max_damage", (double) max);
        variables.put("salvage_durability", max == 0 ? 1 : Math.clamp(1 - (double) stack.getDamageValue() / max, 0, 1));
        variables.put("salvage_enchantment_count", (double) enchantments.size());
        variables.put("salvage_enchantment_levels", enchantments.entrySet().stream().mapToDouble(Object2IntMap.Entry::getIntValue).sum());
        if (rule.outputs().stream().anyMatch(output -> SalvageNumbers.requiresValue(output.amount())))
            ItemValues.get(player.level().getServer(), stack).ifPresent(value -> {
                if (Double.isFinite(value) && value >= 0) variables.put("salvage_value", value);
            });
        FormulaContext numbers = FormulaContext.of(player, variables);
        Map<SpaceContent, Long> contents = new LinkedHashMap<>();
        try {
            for (SalvageRule.Output output : rule.outputs()) {
                SalvageNumbers.validate(output.amount());
                double amount = SalvageNumbers.evaluate(output.amount(), numbers);
                if (amount < 0 || amount >= 0x1.0p63) throw new IllegalArgumentException("Invalid salvage amount");
                long count = (long) Math.floor(amount);
                if (count > 0) contents.merge(output.content(), count, Math::addExact);
            }
        } catch (RuntimeException error) {
            SystemMod.LOGGER.warn("Unable to price salvage outputs for {}", player.getUUID(), error);
            throw new AgentException(AgentFailure.SALVAGE_INVALID_AMOUNT, error);
        }
        if (contents.isEmpty()) throw new AgentException(AgentFailure.SALVAGE_NO_OUTPUT);
        return new Reward(rule.label(), List.of(), contents.entrySet().stream().map(entry -> new SpaceStack(entry.getKey(), entry.getValue())).toList());
    }

    private static void validateReward(ServerPlayer player, Reward reward) {
        SpaceTransfer transfer = new SpaceTransfer(player);
        for (SpaceStack stack : reward.stacks()) {
            // Item quantities live in space and can be withdrawn over multiple visits; do not require backpack room.
            long amount = stack.content() instanceof ItemSpaceContent(
                    ItemStackTemplate item1
            ) ? Math.min(stack.amount(), item1.create().getMaxStackSize()) : stack.amount();
            SpaceContentType.of(stack.content()).prepare(stack.content(), amount, transfer);
        }
    }

    private void validate(ExecutionContext context, ServerPlayer player, Quote quote) {
        if (this.closed || !this.engine.current(context.player(), quote.incarnation()) || quote.player() != player || quote.expires() < System.currentTimeMillis()
                || !this.engine.moduleEnabled(context.player(), quote.owner())
                || player.registryAccess().lookupOrThrow(SystemResourceKeys.SYSTEM_MODULE).getValue(quote.owner()) != quote.module()
                || player.registryAccess().lookupOrThrow(SystemResourceKeys.SALVAGE_RULE) != quote.registry()
                || quote.slot() != player.getInventory().getSelectedSlot() || player.getMainHandItem() != quote.target()
                || !ItemStack.matches(player.getMainHandItem(), quote.input()))
            throw new AgentException(AgentFailure.STALE_QUOTE);
        if (select(player, quote.settings(), player.getMainHandItem()).value() != quote.rule())
            throw new AgentException(AgentFailure.STALE_QUOTE);
        checkInput(player.getMainHandItem());
        CatalogAccess.require(player, quote.rule());
    }

    public CompletionStage<SalvageReceiptOutput> confirm(ExecutionContext context, SalvageConfirmInput input) {
        context.requirePlayerAction(input);
        try {
            UUID.fromString(input.quote());
        } catch (IllegalArgumentException error) {
            throw new AgentException(AgentFailure.INVALID_INPUT, error);
        }
        String key = key(context.player(), input.quote());
        if (!this.active.add(key)) throw new AgentException(AgentFailure.BUSY);
        try {
            SalvageOrder previous = context.store().read(context.player(), data -> ModuleData.get(data, WorkshopSystemModule.STORAGE).order(input.quote()));
            if (previous != null) {
                if (previous.state() == SalvageOrder.State.COMPLETED) {
                    this.active.remove(key);
                    return CompletableFuture.completedFuture(new SalvageReceiptOutput(previous));
                }
                throw new AgentException(previous.state() == SalvageOrder.State.FAILED ? AgentFailure.STALE_QUOTE : AgentFailure.EFFECT_UNKNOWN);
            }
            Quote quote = new CatalogAccess(this.game).read(context, player -> {
                Quote issued = this.quotes.get(context.player());
                if (issued == null || !issued.id().equals(input.quote()))
                    throw new AgentException(AgentFailure.STALE_QUOTE);
                this.validate(context, player, issued);
                return issued;
            });
            String revision = UUID.nameUUIDFromBytes(CodecIO.write(context.store().codec(SalvageRule.CODEC), quote.rule()).getBytes(StandardCharsets.UTF_8)).toString();
            SalvageOrder order = new SalvageOrder(quote.id(), new SalvageOrder.Terms(quote.owner(), quote.ruleId(), this.engine.incarnation(context.player()),
                    revision, quote.input(), quote.payment().getData(), quote.reward()), SalvageOrder.State.PREPARED, System.currentTimeMillis(), "", "", "", 0);
            context.store().update(context.player(), data -> {
                context.check();
                WorkshopDataStorage storage = ModuleData.get(data, WorkshopSystemModule.STORAGE);
                if (storage.order(order.id()) != null) throw new AgentException(AgentFailure.BUSY);
                storage.put(order);
                Delivery held = new Delivery(order.entitlement(), new PersistHolder<>(quote.ruleId(), quote.reward()));
                held.state = "PAYMENT_PENDING";
                ModuleData.get(data, SpaceSystemModule.STORAGE).putDelivery(held.id, held);
                return null;
            });
            Settlement settlement = new Settlement(context.player(), context.store(), order);
            this.pending.put(key, settlement);
            CompletionStage<Boolean> execution;
            try {
                context.store().update(context.player(), data -> {
                    ModuleData.get(data, WorkshopSystemModule.STORAGE).put(order.withState(SalvageOrder.State.EXECUTING, ""));
                    return null;
                });
                execution = this.game.onPlayer(context, player -> {
                    this.validate(context, player, quote);
                    checkEvent(player, quote.ruleId(), player.getMainHandItem());
                    this.validate(context, player, quote);
                    validateReward(player, quote.reward());
                    PlayerFact fact = new PlayerFact(FACT, quote.ruleId(), BuiltInRegistries.ITEM.getKey(quote.target().getItem()).toString(), quote.id(), System.currentTimeMillis());
                    List<PlayerFacts.Award> awards = PlayerFacts.prepare(player, fact);
                    context.check();
                    if (!this.quotes.remove(context.player(), quote))
                        throw new AgentException(AgentFailure.STALE_QUOTE);
                    quote.payment().pay(player, settlement.touched);
                    // A fee implementation must not replace or alter the target as a side effect.
                    // Do not retest acquisition conditions after charging: the fee may intentionally consume their resources.
                    if (!this.engine.current(context.player(), quote.incarnation()) || !this.engine.moduleEnabled(context.player(), quote.owner())
                            || quote.slot() != player.getInventory().getSelectedSlot() || player.getMainHandItem() != quote.target()
                            || !ItemStack.matches(quote.target(), quote.input()))
                        throw new AgentException(AgentFailure.EFFECT_UNKNOWN);
                    settlement.touched.set(true);
                    player.getInventory().setItem(quote.slot(), ItemStack.EMPTY);
                    player.getInventory().setChanged();
                    player.containerMenu.broadcastChanges();
                    settlement.fact = fact;
                    settlement.awards = awards;
                    return true;
                });
            } catch (RuntimeException error) {
                execution = CompletableFuture.failedFuture(error);
            }
            execution.whenComplete((_, error) -> {
                settlement.error = error;
                settlement.ready = true;
                this.finalizeOrder(key, settlement);
            });
            return settlement.result;
        } catch (RuntimeException error) {
            this.active.remove(key);
            throw error;
        }
    }

    private static final class Settlement {
        final UUID player;
        final AgentStore store;
        final SalvageOrder order;
        final AtomicBoolean touched = new AtomicBoolean(), saving = new AtomicBoolean();
        final CompletableFuture<SalvageReceiptOutput> result = new CompletableFuture<>();
        volatile boolean ready;
        volatile long retryAt;
        Throwable error;
        PlayerFact fact;
        List<PlayerFacts.Award> awards = List.of();

        Settlement(UUID player, AgentStore store, SalvageOrder order) {
            this.player = player;
            this.store = store;
            this.order = order;
        }
    }

    private void finalizeOrder(String key, Settlement settlement) {
        if (this.closed || !settlement.ready || !settlement.saving.compareAndSet(false, true)) return;
        this.engine.maintenance(() -> settlement.store.update(settlement.player, data -> {
            WorkshopDataStorage storage = ModuleData.get(data, WorkshopSystemModule.STORAGE);
            SalvageOrder current = storage.order(settlement.order.id());
            if (current == null) throw new AgentException(AgentFailure.STALE_REFERENCE);
            if (current.state() == SalvageOrder.State.COMPLETED) return current;
            SpaceDataStorage space = ModuleData.get(data, SpaceSystemModule.STORAGE);
            SalvageOrder changed;
            if (settlement.error == null) {
                space.setDeliveryState(current.entitlement(), "PENDING");
                changed = current.withState(SalvageOrder.State.COMPLETED, "");
                if (settlement.fact != null) PlayerFacts.record(data, settlement.fact, settlement.awards);
            } else {
                changed = current.withState(settlement.touched.get() ? SalvageOrder.State.UNKNOWN : SalvageOrder.State.FAILED,
                        AgentException.failure(settlement.error).name());
                if (settlement.touched.get()) space.setDeliveryState(current.entitlement(), "PAYMENT_UNKNOWN");
                else space.removeDelivery(current.entitlement());
            }
            storage.put(changed);
            return changed;
        })).whenComplete((order, error) -> {
            if (error == null) {
                this.pending.remove(key, settlement);
                this.active.remove(key);
                if (settlement.error == null) settlement.result.complete(new SalvageReceiptOutput(order));
                else
                    settlement.result.completeExceptionally(new AgentException(settlement.touched.get() ? AgentFailure.EFFECT_UNKNOWN : AgentException.failure(settlement.error), settlement.error));
            } else if (AgentException.failure(error) == AgentFailure.CANCELLED) {
                this.pending.remove(key, settlement);
                this.active.remove(key);
                settlement.result.completeExceptionally(error);
            } else {
                settlement.retryAt = System.currentTimeMillis() + 5000;
                SystemMod.LOGGER.error("Salvage settlement failed; retaining receipt for retry: player={}, order={}", settlement.player, settlement.order.id(), error);
                settlement.result.completeExceptionally(new AgentException(AgentFailure.EFFECT_UNKNOWN, error));
            }
            settlement.saving.set(false);
        });
    }

    public CompletionStage<Boolean> cancel(ExecutionContext context, String id) {
        return this.game.onPlayer(context, _ -> {
            Quote quote = this.quotes.get(context.player());
            return quote != null && quote.id().equals(id) && this.quotes.remove(context.player(), quote);
        });
    }

    private static String key(UUID player, String order) {
        return player + ":" + order;
    }

    private void recover(UUID player, PlayerData data) {
        WorkshopDataStorage storage = ModuleData.get(data, WorkshopSystemModule.STORAGE);
        SpaceDataStorage space = ModuleData.get(data, SpaceSystemModule.STORAGE);
        for (SalvageOrder order : List.copyOf(storage.orders().values())) {
            if (this.active.contains(key(player, order.id()))) continue;
            if (order.state() == SalvageOrder.State.PREPARED) {
                storage.put(order.withState(SalvageOrder.State.FAILED, "NOT_EXECUTED"));
                space.removeDelivery(order.entitlement());
            } else if (order.state() == SalvageOrder.State.EXECUTING) {
                storage.put(order.withState(SalvageOrder.State.UNKNOWN, "RESTARTED"));
                space.setDeliveryState(order.entitlement(), "PAYMENT_UNKNOWN");
            }
        }
    }

    public SalvageHistoryOutput history(ExecutionContext context, int offset) {
        return context.store().read(context.player(), data -> page(data, offset));
    }

    private static SalvageHistoryOutput page(PlayerData data, int offset) {
        List<SalvageOrder> all = ModuleData.get(data, WorkshopSystemModule.STORAGE).orders().values().stream()
                .sorted(Comparator.comparingLong(SalvageOrder::created).reversed().thenComparing(SalvageOrder::id)).toList();
        int start = Math.clamp(offset / 10 * 10, 0, Math.max(0, (all.size() - 1) / 10 * 10));
        return new SalvageHistoryOutput(all.subList(start, Math.min(start + 10, all.size())), start, all.size());
    }

    public CompletionStage<SalvageHistoryOutput> orders(UUID player, int offset) {
        AgentStore store = this.engine.playerStore(player);
        return this.engine.maintenance(() -> store.update(player, data -> {
            this.recover(player, data);
            return page(data, offset);
        }));
    }

    public CompletionStage<SalvageOrder> order(UUID player, String id) {
        AgentStore store = this.engine.playerStore(player);
        return this.engine.maintenance(() -> store.update(player, data -> {
            this.recover(player, data);
            return Optional.ofNullable(ModuleData.get(data, WorkshopSystemModule.STORAGE).order(id))
                    .orElseThrow(() -> new AgentException(AgentFailure.STALE_REFERENCE));
        }));
    }

    public CompletionStage<SalvageOrder> resolve(UUID player, String id, boolean consumed, String reviewer, String reason) {
        if (reason.isBlank() || reason.length() > 512)
            return CompletableFuture.failedFuture(new AgentException(AgentFailure.INVALID_INPUT));
        String key = key(player, id);
        if (!this.active.add(key)) return CompletableFuture.failedFuture(new AgentException(AgentFailure.BUSY));
        AgentStore store = this.engine.playerStore(player);
        return this.engine.maintenance(() -> store.update(player, data -> {
            WorkshopDataStorage storage = ModuleData.get(data, WorkshopSystemModule.STORAGE);
            SalvageOrder order = storage.order(id);
            if (order == null || order.state() != SalvageOrder.State.UNKNOWN && order.state() != SalvageOrder.State.EXECUTING)
                throw new AgentException(AgentFailure.STALE_REFERENCE);
            SpaceDataStorage space = ModuleData.get(data, SpaceSystemModule.STORAGE);
            // No in-process executor owns this order; EXECUTING is a leftover from a previous process.
            if (order.state() == SalvageOrder.State.EXECUTING)
                space.setDeliveryState(order.entitlement(), "PAYMENT_UNKNOWN");
            Delivery delivery = space.getDelivery(order.entitlement());
            if (delivery == null || delivery.stored || !"PAYMENT_UNKNOWN".equals(delivery.state)
                    || space.getEntries().values().stream().anyMatch(entry -> entry.entitlement().filter(order.entitlement()::equals).isPresent()))
                throw new AgentException(AgentFailure.EFFECT_UNKNOWN);
            if (consumed) space.setDeliveryState(order.entitlement(), "PENDING");
            else space.removeDelivery(order.entitlement());
            SalvageOrder reviewed = order.review(consumed, reviewer, reason);
            storage.put(reviewed);
            return reviewed;
        })).thenApply(reviewed -> {
            SystemMod.LOGGER.warn("Salvage order reviewed: player={}, order={}, consumed={}, reviewer={}, reason={}", player, id, consumed, reviewer, reason);
            return reviewed;
        }).whenComplete((_, _) -> this.active.remove(key));
    }

    @Override
    public Session login(ServerPlayer player) {
        return new Session() {
            @Override
            public void initialize(PlayerData data) {
                Salvage.this.recover(player.getUUID(), data);
            }

            @Override
            public void close() {
                Salvage.this.quotes.remove(player.getUUID());
            }
        };
    }

    @Override
    public void tick() {
        long now = System.currentTimeMillis();
        this.quotes.values().removeIf(quote -> quote.expires() < now || !this.engine.moduleEnabled(quote.player().getUUID(), quote.owner())
                || !this.engine.current(quote.player().getUUID(), quote.incarnation())
                || quote.player().getInventory().getSelectedSlot() != quote.slot() || quote.player().getMainHandItem() != quote.target()
                || !ItemStack.matches(quote.target(), quote.input()));
        this.pending.forEach((key, settlement) -> {
            if (settlement.retryAt <= now) this.finalizeOrder(key, settlement);
        });
    }

    @SubscribeEvent
    public void reload(TagsUpdatedEvent.ServerDataLoad event) {
        this.quotes.clear();
    }

    @Override
    public void close() {
        this.closed = true;
        this.quotes.clear();
        NeoForge.EVENT_BUS.unregister(this);
    }
}
