package com.iafenvoy.system.assistant.space;

import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.execution.AgentEngine;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.assistant.MinecraftTools;
import com.iafenvoy.system.assistant.commerce.CommerceTransactions;
import com.iafenvoy.system.assistant.item.ItemSearches;
import com.iafenvoy.system.assistant.signin.SignIns;
import com.iafenvoy.system.data.input.RewardChoiceInput;
import com.iafenvoy.system.data.input.SpaceTakeInput;
import com.iafenvoy.system.data.item.ItemSearchBatch;
import com.iafenvoy.system.data.output.RewardChoiceOutput;
import com.iafenvoy.system.data.output.SpaceOutput;
import com.iafenvoy.system.data.output.StateOutput;
import com.iafenvoy.system.data.progress.EarnedReward;
import com.iafenvoy.system.data.space.*;
import com.iafenvoy.system.data.store.Delivery;
import com.iafenvoy.system.data.store.Reward;
import com.iafenvoy.system.module.ModuleData;
import com.iafenvoy.system.module.PlayerModule;
import com.iafenvoy.system.module.builtin.CoreSystemModule;
import com.iafenvoy.system.module.builtin.SpaceSystemModule;
import com.iafenvoy.system.module.builtin.storage.SpaceDataStorage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared withdrawals serve individual entries and legacy whole-package receipts. All I/O stays off-thread.
 */
public final class SystemSpace implements PlayerModule {
    private record Group(SpaceContent content, boolean blocked, String identity) {
    }

    private record Plan(Map<String, Long> amounts, List<SpaceEntry> entries) {
    }

    private final AgentEngine engine;
    private final MinecraftTools game;
    private final CommerceTransactions commerce;
    private final SignIns signIns;
    private final ItemSearches searches;
    private final AtomicLong revisions = new AtomicLong();

    public SystemSpace(AgentEngine engine, MinecraftTools game, CommerceTransactions commerce, SignIns signIns, ItemSearches searches) {
        this.engine = engine;
        this.game = game;
        this.commerce = commerce;
        this.signIns = signIns;
        this.searches = searches;
    }

    public CompletionStage<SpaceOutput> query(ExecutionContext context) {
        return this.snapshot(context, Component.empty());
    }

    public Optional<PersistHolder<Reward>> reward(ExecutionContext context, String entitlement) {
        return context.store().read(context.player(), data -> {
            Delivery delivery = ModuleData.get(data, SpaceSystemModule.STORAGE).getDelivery(entitlement);
            return delivery == null ? this.signIns.entitlement(context.store(), context.player(), data, entitlement)
                    .or(() -> Optional.ofNullable(ModuleData.get(data, CoreSystemModule.FACTS).rewards().get(entitlement))
                            .filter(EarnedReward::transferred).map(EarnedReward::reward))
                    .or(() -> this.commerce.archivedEntitlement(context.store(), context.player(), data, entitlement)
                            .filter(purchase -> "COMPLETED".equals(purchase.state)).map(purchase -> purchase.order.reward())) : Optional.of(delivery.reward);
        });
    }

    private CompletionStage<SpaceOutput> snapshot(ExecutionContext context, Component feedback) {
        List<SpaceEntry> entries = context.store().read(context.player(), data -> List.copyOf(ModuleData.get(data, SpaceSystemModule.STORAGE).getEntries().values()));
        return this.game.onPlayer(context, _ -> {
            Map<Group, List<SpaceEntry>> groups = new LinkedHashMap<>();
            entries.forEach(entry -> groups.computeIfAbsent(new Group(entry.content(), entry.state() != SpaceEntry.State.AVAILABLE,
                    entry.content() instanceof ChoiceSpaceContent ? entry.id() : ""), _ -> new ArrayList<>()).add(entry));
            List<SpaceOutput.Entry> previews = new ArrayList<>();
            for (Map.Entry<Group, List<SpaceEntry>> group : groups.entrySet())
                previews.add(preview(group.getKey(), group.getValue()));
            return new SpaceOutput(this.revisions.incrementAndGet(), previews, feedback);
        });
    }

    private static SpaceOutput.Entry preview(Group group, List<SpaceEntry> entries) {
        return preview(group.content(), group.blocked(), entries);
    }

    private static <T extends SpaceContent> SpaceOutput.Entry preview(T content, boolean blocked, List<SpaceEntry> entries) {
        SpaceContentType<T> type = SpaceContentType.of(content);
        return new SpaceOutput.Entry(entries.getFirst().id(), type.searchId(content), type.name(content), type.icon(content),
                entries.stream().mapToLong(SpaceEntry::amount).reduce(0, (sum, amount) -> sum > Long.MAX_VALUE - amount ? Long.MAX_VALUE : sum + amount), blocked,
                content instanceof ItemSpaceContent(
                        ItemStackTemplate item
                ) ? Optional.of(item) : Optional.empty(), content instanceof ChoiceSpaceContent);
    }

    private static <T extends SpaceContent> int batch(T content) {
        return Math.max(1, SpaceContentType.of(content).batchSize(content));
    }

    public CompletionStage<SpaceOutput> take(ExecutionContext context, SpaceTakeInput input) {
        Optional<ItemSearchBatch> search = this.searches.candidates(context.player(), input);
        Set<ItemStackTemplate> variants = search.map(batch -> Set.copyOf(batch.items())).orElse(Set.of());
        return this.query(context).thenCompose(snapshot -> this.engine.maintenance(() -> {
            List<SpaceOutput.Entry> candidates = snapshot.entries().stream().filter(entry -> !entry.blocked())
                    .filter(entry -> !input.entry().isBlank() ? entry.id().equals(input.entry())
                            : matches(entry, input.query()) || entry.item().filter(item -> variants.contains(item.withCount(1))).isPresent()).toList();
            // An incomplete client batch cannot prove a unique withdrawal target.
            if (candidates.size() != 1 || search.filter(ItemSearchBatch::hasMore).isPresent())
                return CompletableFuture.completedFuture(new SpaceOutput(snapshot.revision(), snapshot.entries(),
                        Component.translatable(candidates.isEmpty() && search.filter(ItemSearchBatch::hasMore).isEmpty()
                                ? "system.ui.space_missing" : "system.ui.space_ambiguous")));
            String selected = candidates.getFirst().id();
            SpaceContent content = context.store().read(context.player(), data -> {
                SpaceEntry entry = ModuleData.get(data, SpaceSystemModule.STORAGE).getEntries().get(selected);
                if (entry == null) throw new AgentException(AgentFailure.STALE_REFERENCE);
                return entry.content();
            });
            if (content instanceof ChoiceSpaceContent) throw new AgentException(AgentFailure.CHOICE_REQUIRED);
            return this.game.onPlayer(context, _ -> input.stack() ? batch(content) : input.amount())
                    .thenCompose(amount -> this.engine.maintenance(() -> context.store().update(context.player(), data -> {
                        context.check();
                        SpaceDataStorage storage = ModuleData.get(data, SpaceSystemModule.STORAGE);
                        List<SpaceEntry> entries = storage.getEntries().values().stream()
                                .filter(entry -> entry.content().equals(content) && entry.state() == SpaceEntry.State.AVAILABLE).toList();
                        long remaining = amount;
                        Map<String, Long> amounts = new LinkedHashMap<>();
                        for (SpaceEntry entry : entries) {
                            long count = Math.min(remaining, entry.amount());
                            if (count > 0) amounts.put(entry.id(), count);
                            remaining -= count;
                        }
                        if (amounts.isEmpty()) throw new AgentException(AgentFailure.STALE_REFERENCE);
                        storage.reserve(amounts);
                        return new Plan(Map.copyOf(amounts), entries.stream().filter(entry -> amounts.containsKey(entry.id())).toList());
                    }))).thenCompose(plan -> this.transfer(context, plan).thenCompose(_ -> this.engine.maintenance(() -> this.snapshot(context,
                                    Component.translatable("system.ui.space_taken", candidates.getFirst().name(), plan.amounts().values().stream().mapToLong(Long::longValue).sum())))
                            .thenCompose(stage -> stage)));
        })).thenCompose(stage -> stage);
    }

    private static boolean matches(SpaceOutput.Entry entry, String query) {
        String value = query.strip().toLowerCase(Locale.ROOT);
        return entry.searchId().equalsIgnoreCase(value) || entry.name().getString().equalsIgnoreCase(value);
    }

    public CompletionStage<StateOutput> deliver(ExecutionContext context, String entitlement) {
        Plan plan = context.store().update(context.player(), data -> {
            context.check();
            SpaceDataStorage storage = ModuleData.get(data, SpaceSystemModule.STORAGE);
            Delivery delivery = storage.getDelivery(entitlement);
            if (delivery == null) {
                if (this.signIns.entitlement(context.store(), context.player(), data, entitlement).isPresent()
                        || Optional.ofNullable(ModuleData.get(data, CoreSystemModule.FACTS).rewards().get(entitlement)).filter(EarnedReward::transferred).isPresent()
                        || this.commerce.archivedEntitlement(context.store(), context.player(), data, entitlement)
                        .filter(purchase -> "COMPLETED".equals(purchase.state)).isPresent())
                    return new Plan(Map.of(), List.of());
                throw new AgentException(AgentFailure.NOT_AUTHORIZED);
            }
            if ("DELIVERED".equals(delivery.state)) return new Plan(Map.of(), List.of());
            if (!"PENDING".equals(delivery.state)) throw new AgentException(AgentFailure.EFFECT_UNKNOWN);
            List<SpaceEntry> entries = storage.getEntries().values().stream()
                    .filter(entry -> entry.entitlement().filter(entitlement::equals).isPresent()).toList();
            if (entries.stream().anyMatch(entry -> entry.content() instanceof ChoiceSpaceContent))
                return new Plan(Map.of(), entries);
            Map<String, Long> amounts = new LinkedHashMap<>();
            entries.forEach(entry -> amounts.put(entry.id(), entry.amount()));
            if (amounts.isEmpty()) throw new AgentException(AgentFailure.STALE_REFERENCE);
            storage.reserve(amounts);
            storage.setDeliveryState(entitlement, "DELIVERING");
            return new Plan(Map.copyOf(amounts), entries);
        });
        return plan.amounts().isEmpty() && !plan.entries().isEmpty() ? CompletableFuture.completedFuture(new StateOutput("CHOICE_REQUIRED"))
                : plan.entries().isEmpty() ? CompletableFuture.completedFuture(new StateOutput("DELIVERED"))
                : this.transfer(context, plan).thenApply(_ -> new StateOutput("DELIVERED"));
    }

    public RewardChoiceOutput choice(ExecutionContext context, RewardChoiceInput input) {
        if (input.ticket() <= 0) throw new AgentException(AgentFailure.INVALID_INPUT);
        if (input.confirm()) {
            context.requirePlayerAction(input);
            return context.store().update(context.player(), data -> {
                context.check();
                return new RewardChoiceOutput(ModuleData.get(data, SpaceSystemModule.STORAGE).choose(input.entry(), input.ticket(), input.option()));
            });
        }
        return context.store().read(context.player(), data -> {
            SpaceDataStorage space = ModuleData.get(data, SpaceSystemModule.STORAGE);
            ChoiceReceipt previous = space.choices().get(ChoiceReceipt.key(input.entry(), input.ticket()));
            if (previous != null) return new RewardChoiceOutput(previous);
            SpaceEntry entry = space.getEntries().get(input.entry());
            if (entry == null || entry.state() != SpaceEntry.State.AVAILABLE || entry.amount() != input.ticket()
                    || !(entry.content() instanceof ChoiceSpaceContent choice))
                throw new AgentException(AgentFailure.STALE_REFERENCE);
            if (input.option() < -1 || input.option() >= choice.options().size())
                throw new AgentException(AgentFailure.INVALID_SELECTION);
            return new RewardChoiceOutput(entry.id(), input.ticket(), choice.name(), choice.options(), input.option(), false);
        });
    }

    private static <T extends SpaceContent> void prepare(T content, long amount, SpaceTransfer transfer) {
        SpaceContentType.of(content).prepare(content, amount, transfer);
    }

    private static SpaceTransfer prepare(Plan plan, ServerPlayer player) {
        SpaceTransfer transfer = new SpaceTransfer(player);
        plan.entries().forEach(entry -> prepare(entry.content(), plan.amounts().get(entry.id()), transfer));
        transfer.validate();
        return transfer;
    }

    private CompletionStage<Void> transfer(ExecutionContext context, Plan plan) {
        AtomicBoolean touched = new AtomicBoolean();
        return this.game.onPlayer(context, player -> prepare(plan, player)).thenCompose(transfer -> this.engine.maintenance(() -> {
            context.check();
            context.store().read(context.player(), data -> {
                transfer.credit(data);
                return null;
            });
            return transfer;
        })).thenCompose(transfer -> this.game.onPlayer(context, player -> {
            if (transfer.player() != player) throw new AgentException(AgentFailure.PLAYER_OFFLINE);
            // Contents, permissions and inventory may have changed while the worker validated wallet capacity.
            SpaceTransfer current = prepare(plan, player);
            context.check();
            if (current.hasGameEffects()) {
                touched.set(true);
                current.apply();
            }
            return current;
        })).handle((transfer, error) -> this.engine.maintenance(() -> {
            if (error != null) {
                this.finish(context, plan, false, touched.get());
                throw new AgentException(touched.get() ? AgentFailure.EFFECT_UNKNOWN : AgentException.failure(error), error);
            }
            try {
                context.store().update(context.player(), data -> {
                    if (!touched.get()) context.check();
                    transfer.credit(data);
                    ModuleData.get(data, SpaceSystemModule.STORAGE).finish(plan.amounts(), true, touched.get());
                    return null;
                });
            } catch (RuntimeException failure) {
                this.finish(context, plan, false, touched.get());
                throw new AgentException(touched.get() ? AgentFailure.EFFECT_UNKNOWN : AgentException.failure(failure), failure);
            }
            return (Void) null;
        })).thenCompose(stage -> stage);
    }

    private void finish(ExecutionContext context, Plan plan, boolean succeeded, boolean touched) {
        context.store().update(context.player(), data -> {
            ModuleData.get(data, SpaceSystemModule.STORAGE).finish(plan.amounts(), succeeded, touched);
            return null;
        });
    }
}
