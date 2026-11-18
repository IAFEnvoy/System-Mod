package com.iafenvoy.system.assistant.commerce;

import com.iafenvoy.saturn.runtime.resulting.Failure;
import com.iafenvoy.saturn.util.HolderHelper;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.execution.AgentEngine;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.storage.AgentStore;
import com.iafenvoy.system.assistant.MinecraftTools;
import com.iafenvoy.system.assistant.catalog.CatalogAccess;
import com.iafenvoy.system.assistant.space.SpaceTransfer;
import com.iafenvoy.system.config.file.AgentConfig;
import com.iafenvoy.system.data.LotteryPool;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.catalog.CommerceCatalog;
import com.iafenvoy.system.data.catalog.ShopOffer;
import com.iafenvoy.system.data.catalog.ShopTrade;
import com.iafenvoy.system.data.input.CommercePreviewInput;
import com.iafenvoy.system.data.input.LotteryHistoryInput;
import com.iafenvoy.system.data.output.*;
import com.iafenvoy.system.data.result.ShopOrder;
import com.iafenvoy.system.data.space.SpaceContentType;
import com.iafenvoy.system.data.space.SpaceStack;
import com.iafenvoy.system.data.store.*;
import com.iafenvoy.system.module.ModuleData;
import com.iafenvoy.system.module.ModuleStorage;
import com.iafenvoy.system.module.PlayerModule;
import com.iafenvoy.system.module.builtin.SpaceSystemModule;
import com.iafenvoy.system.module.builtin.storage.CommerceDataStorage;
import com.iafenvoy.system.module.builtin.storage.SpaceDataStorage;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Payment touches inventory on the game thread; durable order and space writes run off it.
 */
public final class CommerceTransactions {
    private final AgentEngine engine;
    private final MinecraftTools game;
    private final CommerceCatalogs catalogs;
    private final CommerceArchive archive;
    private final CommerceQuotes quotes = new CommerceQuotes();
    private final Set<String> active = ConcurrentHashMap.newKeySet();
    private final Map<PurchaseType, ModuleStorage<CommerceDataStorage>> storages = new EnumMap<>(PurchaseType.class);

    public CommerceTransactions(AgentEngine engine, MinecraftTools game, CommerceCatalogs catalogs, CommerceArchive archive) {
        this.engine = engine;
        this.game = game;
        this.catalogs = catalogs;
        this.archive = archive;
        engine.lifecycle((player, event) -> {
            switch (event) {
                case REVOKED -> {
                    this.quotes.clear(player);
                    this.archive.delete(player);
                }
                case ACCESS_CHANGED -> this.quotes.clear(player);
            }
        });
    }

    private ModuleStorage<CommerceDataStorage> storage(PurchaseType type) {
        ModuleStorage<CommerceDataStorage> storage = this.storages.get(type);
        if (storage == null) throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
        return storage;
    }

    /**
     * Each commerce module restores only its own journal; disabling a module never discards orders.
     */
    public PlayerModule runtime(PurchaseType type, ModuleStorage<CommerceDataStorage> storage) {
        ModuleStorage<CommerceDataStorage> previous = this.storages.putIfAbsent(type, storage);
        if (previous != null && previous != storage)
            throw new IllegalArgumentException("Duplicate commerce module " + type);
        return new PlayerModule() {
            @Override
            public Session login(ServerPlayer player) {
                UUID uuid = player.getUUID();
                AgentStore store = CommerceTransactions.this.engine.playerStore(uuid);
                return data -> {
                    CommerceDataStorage commerce = ModuleData.get(data, storage);
                    commerce.streamPurchases()
                            .filter(purchase -> "PREPARED".equals(purchase.state) && !CommerceTransactions.this.active(uuid, purchase.id))
                            .forEach(purchase -> {
                                commerce.setPurchaseState(purchase.id, "PAYMENT_UNKNOWN", "EFFECT_UNKNOWN");
                                SpaceDataStorage inbox = ModuleData.get(data, SpaceSystemModule.STORAGE);
                                String entitlement = purchase.order.type().entitlement(purchase.id);
                                if (inbox.getDelivery(entitlement) != null)
                                    inbox.setDeliveryState(entitlement, "PAYMENT_UNKNOWN");
                            });
                    CommerceTransactions.this.compact(store, uuid, data, type, false);
                };
            }
        };
    }

    public boolean active(UUID player, String order) {
        return this.active.contains(player + ":" + order);
    }

    /**
     * Both member and group ledgers remain readable after their offering module is disabled.
     */
    public List<ShopOrder> pending(ExecutionContext context) {
        return context.store().read(context.player(), data -> this.storages.values().stream()
                .flatMap(storage -> data.moduleData.get(storage.owner(), storage.type()).stream())
                .flatMap(CommerceDataStorage::streamPurchases)
                .filter(purchase -> !"COMPLETED".equals(purchase.state) && !"FAILED".equals(purchase.state))
                .limit(8).map(purchase -> new ShopOrder(purchase.id, purchase.order.label(), purchase.state, purchase.error)).toList());
    }

    /**
     * Archive only settled and fully withdrawn orders. Write the archive before removing either live record.
     */
    private int compact(AgentStore store, UUID player, PlayerData data, PurchaseType type, boolean all) {
        CommerceDataStorage commerce = ModuleData.get(data, this.storage(type));
        if (type == PurchaseType.LOTTERY) this.restoreLotteryCounts(store, player, commerce);
        if (!all && commerce.getPurchases().size() < 128) return 0;
        SpaceDataStorage space = ModuleData.get(data, SpaceSystemModule.STORAGE);
        Set<String> remaining = new HashSet<>();
        space.getEntries().values().forEach(entry -> entry.entitlement().ifPresent(remaining::add));
        List<Purchase> settled = commerce.streamPurchases().filter(purchase -> !this.active(player, purchase.id))
                .filter(purchase -> {
                    Delivery delivery = space.getDelivery(type.entitlement(purchase.id));
                    return ("FAILED".equals(purchase.state) && delivery == null) || ("COMPLETED".equals(purchase.state)
                            && delivery != null && "DELIVERED".equals(delivery.state)
                            && !remaining.contains(delivery.id));
                }).sorted(Comparator.comparingLong(purchase -> purchase.created)).toList();
        int count = Math.max(0, settled.size() - (all ? 0 : 64));
        long now = System.currentTimeMillis();
        for (Purchase purchase : settled.subList(0, count)) {
            this.archive.put(store, player, commerce.archiveScope(), purchase);
            commerce.archivePurchase(purchase.id, now);
            space.removeDelivery(type.entitlement(purchase.id));
        }
        return count;
    }

    private void restoreLotteryCounts(AgentStore store, UUID player, CommerceDataStorage commerce) {
        if (!commerce.lotteryCountsReady())
            commerce.initializeLotteryCounts(this.archive.rewardCounts(store, player, commerce.archiveScope(), commerce.getPurchases().keySet()));
    }

    public Optional<Purchase> archivedEntitlement(AgentStore store, UUID player, PlayerData data, String entitlement) {
        for (PurchaseType type : PurchaseType.values()) {
            String prefix = type.getSerializedName() + ":";
            if (entitlement.startsWith(prefix) && this.storages.containsKey(type))
                return this.archive.get(store, player, ModuleData.get(data, this.storage(type)).archiveScope(), type, entitlement.substring(prefix.length()));
        }
        return Optional.empty();
    }

    public record OrderPage(List<Purchase> orders, int offset, int total) {
    }

    /**
     * Management calls capture a guarded store before entering workers, so revoke invalidates pending edits.
     */
    public CompletionStage<OrderPage> orders(UUID player, PurchaseType type, int requested) {
        AgentStore store = this.engine.playerStore(player);
        return this.engine.maintenance(() -> store.read(player, data -> {
            List<Purchase> orders = ModuleData.get(data, this.storage(type)).streamPurchases()
                    .sorted(Comparator.<Purchase, Boolean>comparing(purchase -> !Set.of("PREPARED", "PAYMENT_UNKNOWN").contains(purchase.state))
                            .thenComparing(Comparator.comparingLong((Purchase purchase) -> purchase.created).reversed())).toList();
            int offset = Math.min(Math.max(0, requested), Math.max(0, orders.size() - 1));
            return new OrderPage(orders.subList(offset, Math.min(offset + 20, orders.size())), offset, orders.size());
        }));
    }

    public CompletionStage<Purchase> order(UUID player, PurchaseType type, String id) {
        AgentStore store = this.engine.playerStore(player);
        return this.engine.maintenance(() -> store.read(player, data -> Optional.ofNullable(ModuleData.get(data, this.storage(type)).getPurchase(id))
                .or(() -> this.archive.get(store, player, ModuleData.get(data, this.storage(type)).archiveScope(), type, id))
                .orElseThrow(() -> new AgentException(AgentFailure.STALE_REFERENCE))));
    }

    public CompletionStage<Integer> archive(UUID player, PurchaseType type) {
        AgentStore store = this.engine.playerStore(player);
        return this.engine.maintenance(() -> store.update(player, data -> this.compact(store, player, data, type, true)));
    }

    public CompletionStage<Purchase> resolve(UUID player, PurchaseType type, String id, boolean paid, String reviewer) {
        AgentStore store = this.engine.playerStore(player);
        String key = player + ":" + id;
        if (!this.active.add(key)) return CompletableFuture.failedFuture(new AgentException(AgentFailure.BUSY));
        return this.engine.maintenance(() -> store.update(player, data -> {
            CommerceDataStorage commerce = ModuleData.get(data, this.storage(type));
            Purchase purchase = commerce.getPurchase(id);
            if (purchase == null || purchase.order.type() != type)
                throw new AgentException(AgentFailure.STALE_REFERENCE);
            if (!Set.of("PAYMENT_UNKNOWN", "PREPARED").contains(purchase.state))
                throw new AgentException(AgentFailure.STALE_REFERENCE);
            if (type == PurchaseType.LOTTERY) this.restoreLotteryCounts(store, player, commerce);
            SpaceDataStorage space = ModuleData.get(data, SpaceSystemModule.STORAGE);
            String entitlement = type.entitlement(id);
            Delivery held = space.getDelivery(entitlement);
            // Payment review may never rewrite a reward that has already been published or partially withdrawn.
            if ((held != null && (held.stored || !Set.of("PAYMENT_UNKNOWN", "PAYMENT_PENDING").contains(held.state)))
                    || space.getEntries().values().stream().anyMatch(entry -> entry.entitlement().filter(entitlement::equals).isPresent()))
                throw new AgentException(AgentFailure.EFFECT_UNKNOWN);
            if (paid) {
                if (held == null) space.putDelivery(entitlement, new Delivery(entitlement, purchase.order.reward()));
                else space.setDeliveryState(entitlement, "PENDING");
                commerce.setPurchaseState(id, "COMPLETED", "");
                commerce.completeLottery(purchase, System.currentTimeMillis());
            } else {
                space.removeDelivery(entitlement);
                commerce.setPurchaseState(id, "FAILED", AgentFailure.INSUFFICIENT_COST.name());
            }
            purchase.review = paid ? "paid" : "unpaid";
            purchase.reviewer = reviewer;
            purchase.reviewed = System.currentTimeMillis();
            return purchase;
        })).whenComplete((purchase, error) -> {
            this.active.remove(key);
            if (error == null)
                SystemMod.LOGGER.info("Commerce payment reviewed: player={}, type={}, order={}, decision={}, reviewer={}",
                        player, type, id, paid ? "paid" : "unpaid", reviewer);
            else
                SystemMod.LOGGER.warn("Commerce payment review failed: player={}, type={}, order={}, reviewer={}", player, type, id, reviewer, error);
        });
    }

    public Map<Identifier, ShopPurchaseLimitOutput> shopLimits(ExecutionContext context, List<ShopOffer> offers) {
        if (offers.stream().noneMatch(offer -> offer.value().purchaseLimit().isPresent())) return Map.of();
        ZoneId zone = ZoneId.of(AgentConfig.INSTANCE.get().timeZone());
        return context.store().read(context.player(), data -> {
            CommerceDataStorage commerce = ModuleData.get(data, this.storage(PurchaseType.SHOP));
            long now = System.currentTimeMillis();
            Map<Identifier, ShopPurchaseLimitOutput> limits = new HashMap<>();
            for (ShopOffer offer : offers) {
                Identifier id = offer.id();
                offer.value().purchaseLimit().ifPresent(limit -> limits.put(id, new ShopPurchaseLimitOutput(limit,
                        commerce.purchaseCount(PurchaseType.SHOP, id, limit.period().start(now, zone)))));
            }
            return Map.copyOf(limits);
        });
    }

    /**
     * Pending/unknown payments keep fixed stock preferred until the quota is actually settled.
     */
    private FixedStock fixedStock(ExecutionContext context) {
        CommerceCatalog available = this.catalogs.available(context.player());
        List<ShopOffer> fixed = available.offers();
        String revision = available.shopRevision();
        ZoneId zone = ZoneId.of(AgentConfig.INSTANCE.get().timeZone());
        if (fixed.stream().noneMatch(offer -> offer.value().purchaseLimit().isPresent()))
            return new FixedStock(Map.of(), fixed, revision, System.currentTimeMillis(), zone);
        return context.store().read(context.player(), data -> {
            CommerceDataStorage commerce = ModuleData.get(data, this.storage(PurchaseType.SHOP));
            Map<Identifier, ShopPurchaseLimitOutput> limits = new HashMap<>();
            long now = System.currentTimeMillis();
            Map<Identifier, Long> periods = new HashMap<>();
            for (ShopOffer offer : fixed)
                offer.value().purchaseLimit().ifPresent(limit ->
                        periods.put(offer.id(), limit.period().start(now, zone)));
            Map<Identifier, Integer> counts = commerce.settledPurchaseCounts(PurchaseType.SHOP, periods);
            for (ShopOffer offer : fixed)
                offer.value().purchaseLimit().ifPresent(limit -> limits.put(offer.id(), new ShopPurchaseLimitOutput(limit, counts.getOrDefault(offer.id(), 0))));
            return new FixedStock(Map.copyOf(limits), fixed, revision, now, zone);
        });
    }

    private record FixedStock(Map<Identifier, ShopPurchaseLimitOutput> limits, List<ShopOffer> offers, String revision, long capturedAt, ZoneId zone) {
        private static final FixedStock EMPTY = new FixedStock(Map.of(), List.of(), "", 0, ZoneId.of("UTC"));

        private Map<Identifier, ShopPurchaseLimitOutput> current() {
            long now = System.currentTimeMillis();
            // Completed counts are monotonic within a period. Crossing a reset requires a fresh worker snapshot.
            if (this.limits.values().stream().anyMatch(value -> value.limit().period().start(this.capturedAt, this.zone)
                    != value.limit().period().start(now, this.zone)))
                throw new AgentException(AgentFailure.STALE_QUOTE);
            return this.limits;
        }
    }

    public CommerceCatalogs.View shopView(ExecutionContext context, ShopRequest request) {
        FixedStock stock = this.fixedStock(context);
        return this.catalogs.access().read(context, player -> this.catalogs.view(player, request, stock.current(), stock.revision()));
    }

    public CompletionStage<List<CommercePreviewOutput>> preview(ExecutionContext context, CommercePreviewInput input) {
        if (input.count() < 1 || input.count() > 100) throw new AgentException(AgentFailure.INVALID_INPUT);
        // Read the journal on the calling worker; the game thread only evaluates payment conditions.
        CommerceCatalog snapshot = this.catalogs.available(context.player());
        ShopRequest request = ShopRequest.resolve(Set.copyOf(input.offers()));
        FixedStock blocking = input.offers().isEmpty() ? FixedStock.EMPTY : this.fixedStock(context);
        Map<Identifier, ShopPurchaseLimitOutput> limits = this.shopLimits(context, blocking.offers().stream()
                .filter(offer -> input.offers().contains(offer.id())).toList());
        if (!input.lotteries().isEmpty() && !context.store().read(context.player(), data -> ModuleData.get(data, this.storage(PurchaseType.LOTTERY)).lotteryCountsReady()))
            context.store().update(context.player(), data -> {
                this.restoreLotteryCounts(context.store(), context.player(), ModuleData.get(data, this.storage(PurchaseType.LOTTERY)));
                return null;
            });
        Map<Identifier, LotteryStatusOutput> lotteries = context.store().read(context.player(), data -> {
            Map<Identifier, LotteryStatusOutput> result = new HashMap<>();
            snapshot.lotteries().stream().filter(pool -> input.lotteries().contains(HolderHelper.id(pool)))
                    .forEach(pool -> result.put(HolderHelper.id(pool), this.lotteryStatus(data, pool, input.count(), System.currentTimeMillis())));
            return result;
        });
        return this.game.onPlayer(context, player -> {
            CommerceCatalogs.View view = this.catalogs.view(player, request, blocking.current(), blocking.revision());
            CommerceCatalog catalog = view.catalog();
            if (!(input.offers().isEmpty() ? catalog.revision() : catalog.shopRevision()).equals(input.revision()))
                throw new AgentException(AgentFailure.STALE_QUOTE);
            List<CommercePreviewOutput> previews = new ArrayList<>();
            for (Identifier id : input.offers()) {
                ShopOffer offer = catalog.offers().stream().filter(value -> value.id().equals(id)).findFirst()
                        .orElseThrow(() -> new AgentException(AgentFailure.STALE_QUOTE));
                CommerceCosts.Prepared payment = CommerceCosts.prepare(offer, player, input.count());
                Optional<ShopPurchaseLimitOutput> limit = Optional.ofNullable(limits.get(id));
                boolean conditionMet = !view.lockedOffers().contains(id);
                boolean available = conditionMet && payment.isSuccess() && limit.map(value -> value.remaining() >= input.count()).orElse(true)
                        && (offer.value().trade() != ShopTrade.BUYBACK || payment.payable());
                Optional<String> reference = input.quote() && available
                        ? Optional.of(this.quotes.create(context.player(), catalog, offer, payment, input.count()).id()) : Optional.empty();
                previews.add(new CommercePreviewOutput(id, payment.getData(), available, reference, limit, conditionMet, Optional.empty(), Optional.of(offer.revision())));
            }
            for (Identifier id : input.lotteries()) {
                Holder<LotteryPool> pool = catalog.lotteries().stream().filter(value -> HolderHelper.id(value).equals(id)).findFirst()
                        .orElseThrow(() -> new AgentException(AgentFailure.STALE_QUOTE));
                if (!pool.value().drawCounts().contains(input.count()))
                    throw new AgentException(AgentFailure.INVALID_INPUT);
                LotteryStatusOutput status = lotteries.get(id);
                if (status == null) throw new AgentException(AgentFailure.STALE_QUOTE);
                CommerceCosts.Prepared payment = CommerceCosts.prepare(pool.value().costs(), player, PurchaseType.LOTTERY, id, input.count());
                boolean conditionMet = !view.lockedLotteries().contains(id);
                boolean available = conditionMet && payment.isSuccess() && status.available(input.count());
                Optional<String> reference = input.quote() && available
                        ? Optional.of(this.quotes.createPool(context.player(), catalog, pool, payment, input.count()).id()) : Optional.empty();
                previews.add(new CommercePreviewOutput(id, payment.getData(), available, reference, Optional.empty(), conditionMet, Optional.of(status), Optional.empty()));
            }
            return List.copyOf(previews);
        });
    }

    private LotteryStatusOutput lotteryStatus(PlayerData data, Holder<LotteryPool> holder, int count, long now) {
        Identifier id = HolderHelper.id(holder);
        LotteryPool pool = holder.value();
        CommerceDataStorage commerce = ModuleData.get(data, this.storage(PurchaseType.LOTTERY));
        LotteryProgress progress = commerce.progress(id);
        int used = pool.drawLimit().map(limit -> commerce.purchaseCount(PurchaseType.LOTTERY, id,
                limit.period().start(now, ZoneId.of(AgentConfig.INSTANCE.get().timeZone())))).orElse(0);
        boolean pending = commerce.streamPurchases().anyMatch(order -> order.order.source().equals(id)
                && Set.of("PREPARED", "PAYMENT_UNKNOWN").contains(order.state));
        long remaining = progress.lastDraw() == 0 ? 0 : Math.max(0, progress.lastDraw() + pool.cooldown() * 1000L - now);
        List<LotteryPool.Entry> eligible = pool.eligible(progress.wins());
        List<LotteryPool.Entry> targets = eligible.stream()
                .filter(entry -> pool.pity().map(pity -> pity.rewards().contains(entry.reward().id())).orElse(false)).toList();
        Map<Identifier, Integer> wins = new HashMap<>();
        pool.entries().stream().filter(entry -> entry.maxWins() > 0)
                .forEach(entry -> wins.put(entry.reward().id(), progress.wins().getOrDefault(entry.reward().id(), 0)));
        return new LotteryStatusOutput(pool.drawCounts(), count, pool.drawLimit(), used, pool.pity(), progress.misses(),
                Math.ceilDiv(remaining, 1000L), pending, CardBuilder.join(targets.stream().map(entry -> entry.reward().value().label()).toList(), " · "),
                wins, pool.remaining(progress.wins()), !targets.isEmpty());
    }

    public LotteryHistoryOutput history(ExecutionContext context, LotteryHistoryInput input) {
        return context.store().read(context.player(), data -> {
            CommerceDataStorage commerce = ModuleData.get(data, this.storage(PurchaseType.LOTTERY));
            if (input.order().isPresent()) {
                String id = input.order().get();
                Purchase order = Optional.ofNullable(commerce.getPurchase(id))
                        .or(() -> this.archive.get(context.store(), context.player(), commerce.archiveScope(), PurchaseType.LOTTERY, id))
                        .orElseThrow(() -> new AgentException(AgentFailure.STALE_REFERENCE));
                if (input.pool().filter(pool -> !pool.equals(order.order.source())).isPresent())
                    throw new AgentException(AgentFailure.INVALID_INPUT);
                boolean complete = "COMPLETED".equals(order.state);
                int total = complete ? order.order.count() : 0;
                int offset = Math.min(Math.max(0, input.offset()) / 20, Math.max(0, total - 1) / 20) * 20;
                return new LotteryHistoryOutput(List.of(historyEntry(order, complete, offset)), input.pool(), offset, total, input.order());
            }
            List<Purchase> live = commerce.streamPurchases().filter(order -> input.pool().map(pool -> pool.equals(order.order.source())).orElse(true)).toList();
            CommerceArchive.Page page = this.archive.history(context.store(), context.player(), commerce.archiveScope(), input.pool(), live, input.offset(), 20);
            return new LotteryHistoryOutput(page.orders().stream().map(order -> historyEntry(order, false, 0)).toList(), input.pool(), page.offset(), page.total(), Optional.empty());
        });
    }

    private static LotteryHistoryOutput.Entry historyEntry(Purchase order, boolean details, int offset) {
        List<LotteryHistoryOutput.Result> results = details
                ? order.order.lottery().map(LotteryRolls::rolls).orElseGet(() -> List.of(new LotteryRolls.Roll(order.order.reward(), false))).stream()
                .skip(offset).limit(20).map(roll -> new LotteryHistoryOutput.Result(roll.reward().value().label(),
                        CardBuilder.join(roll.reward().value().stacks().stream().map(SpaceStack::label).toList(), " · "), roll.guaranteed())).toList() : List.of();
        return new LotteryHistoryOutput.Entry(order.id, order.order.source(), order.order.label(), order.created, order.state, order.order.count(), results);
    }

    public CompletionStage<PurchaseReceiptOutput> execute(ExecutionContext context, String id, PurchaseType type) {
        try {
            UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new AgentException(AgentFailure.INVALID_INPUT);
        }
        ModuleStorage<CommerceDataStorage> storage = this.storage(type);
        Optional<Purchase> previous = context.store().read(context.player(), data -> Optional.ofNullable(ModuleData.get(data, storage).getPurchase(id))
                .or(() -> this.archive.get(context.store(), context.player(), ModuleData.get(data, storage).archiveScope(), type, id).map(purchase -> {
                    if ("COMPLETED".equals(purchase.state)) purchase.state = "DELIVERED";
                    return purchase;
                })));
        if (previous.isPresent()) {
            Purchase purchase = previous.get();
            if (purchase.order.type() != type) throw new AgentException(AgentFailure.NOT_AUTHORIZED);
            if (Set.of("COMPLETED", "DELIVERED").contains(purchase.state))
                return CompletableFuture.completedFuture(receipt(purchase));
            throw new AgentException(purchase.state.equals("FAILED") ? AgentFailure.fromCode(purchase.error) : AgentFailure.EFFECT_UNKNOWN);
        }
        CommerceQuotes.Quote issued = this.quotes.lookup(context.player(), id, this.catalogs.all().revision(), type);
        ShopRequest request = type == PurchaseType.SHOP ? ShopRequest.resolve(Set.of(issued.source())) : ShopRequest.EMPTY;
        CommerceCatalog checked = type == PurchaseType.SHOP ? this.shopView(context, request).catalog() : this.catalogs.available(context.player());
        CommerceQuotes.Quote quote = this.quotes.require(context.player(), id, checked, type);
        String key = context.player() + ":" + id;
        if (!this.active.add(key)) throw new AgentException(AgentFailure.BUSY);
        List<LotteryPool.Entry> candidates;
        try {
            context.ensureMemoryCurrent();
            candidates = context.store().update(context.player(), data -> {
                this.compact(context.store(), context.player(), data, type, false);
                CommerceDataStorage commerce = ModuleData.get(data, storage);
                if (commerce.getPurchase(id) != null || this.archive.get(context.store(), context.player(), commerce.archiveScope(), type, id).isPresent())
                    throw new AgentException(AgentFailure.EFFECT_UNKNOWN);
                long now = System.currentTimeMillis();
                // Check and reserve under the same player transaction, so concurrent quotes cannot exceed the limit.
                quote.purchaseLimit().ifPresent(limit -> {
                    long since = limit.period().start(now, ZoneId.of(AgentConfig.INSTANCE.get().timeZone()));
                    if ((long) commerce.purchaseCount(type, quote.source(), since) + quote.count() > limit.count())
                        throw new AgentException(type == PurchaseType.LOTTERY ? AgentFailure.DRAW_LIMIT_REACHED : AgentFailure.PURCHASE_LIMIT_REACHED);
                });
                quote.pool().ifPresent(pool -> {
                    boolean pending = commerce.streamPurchases().anyMatch(order -> order.order.source().equals(quote.source())
                            && Set.of("PREPARED", "PAYMENT_UNKNOWN").contains(order.state));
                    if (pending) throw new AgentException(AgentFailure.BUSY);
                    LotteryProgress progress = commerce.progress(quote.source());
                    if (progress.lastDraw() != 0 && now - progress.lastDraw() < pool.cooldown() * 1000L)
                        throw new AgentException(AgentFailure.LOTTERY_COOLDOWN);
                    if (quote.count() > pool.remaining(progress.wins()))
                        throw new AgentException(AgentFailure.LOTTERY_EXHAUSTED);
                });
                commerce.assertPurchaseCount();
                SpaceDataStorage inbox = ModuleData.get(data, SpaceSystemModule.STORAGE);
                inbox.assertDeliveryCount();
                Purchase purchase = new Purchase();
                purchase.id = id;
                purchase.order = quote.select(commerce.progress(quote.source()));
                purchase.created = now;
                commerce.putPurchase(id, purchase);
                // Reserve the reward before payment; delivery becomes available only after payment is recorded.
                Delivery held = new Delivery(type.entitlement(id), purchase.order.reward());
                held.state = "PAYMENT_PENDING";
                inbox.putDelivery(held.id, held);
                return quote.pool().map(pool -> pool.eligible(commerce.progress(quote.source()).wins())).orElse(quote.entries());
            });
        } catch (RuntimeException e) {
            this.active.remove(key);
            throw e;
        }

        // Once payment may have changed external state, failures become UNKNOWN and must not auto-retry.
        AtomicBoolean touched = new AtomicBoolean();
        CompletionStage<Boolean> payment;
        try {
            FixedStock blocking = type == PurchaseType.SHOP ? this.fixedStock(context) : FixedStock.EMPTY;
            payment = this.game.onPlayer(context, player -> {
                CommerceCatalog current = this.catalogs.view(player, request, blocking.current(), blocking.revision()).catalog();
                this.quotes.require(context.player(), id, current, type);
                switch (type) {
                    case SHOP ->
                            CatalogAccess.require(player, current.offers().stream().filter(holder -> holder.id().equals(quote.source()))
                                    .findFirst().orElseThrow(() -> new AgentException(AgentFailure.STALE_QUOTE)).value());
                    case LOTTERY ->
                            CatalogAccess.require(player, current.lotteries().stream().filter(holder -> HolderHelper.id(holder).equals(quote.source()))
                                    .findFirst().orElseThrow(() -> new AgentException(AgentFailure.STALE_QUOTE)).value());
                }
                // Stage every possible reward before payment without executing effects or requiring backpack room.
                // Rejecting only the selected prize would bias the pool's odds.
                for (LotteryPool.Entry entry : candidates) {
                    SpaceTransfer transfer = new SpaceTransfer(player);
                    entry.reward().value().stacks().forEach(stack -> SpaceContentType.of(stack.content()).prepareReward(stack.content(), stack.amount(), transfer));
                }
                context.check();
                quote.payment().pay(player, touched);
                return true;
            });
        } catch (RuntimeException error) {
            payment = CompletableFuture.failedFuture(error);
        }

        CompletableFuture<PurchaseReceiptOutput> result = new CompletableFuture<>();
        payment.whenComplete((paid, error) -> this.engine.maintenance(() -> {
            Failure failure = error == null ? null : touched.get() ? AgentFailure.EFFECT_UNKNOWN : AgentException.failure(error);
            PurchaseReceiptOutput committed = context.store().update(context.player(), data -> {
                CommerceDataStorage commerce = ModuleData.get(data, storage);
                Purchase order = commerce.getPurchase(id);
                SpaceDataStorage inbox = ModuleData.get(data, SpaceSystemModule.STORAGE);
                if (error == null) {
                    String entitlement = type.entitlement(id);
                    inbox.setDeliveryState(entitlement, "PENDING");
                    commerce.setPurchaseState(id, "COMPLETED", order.error);
                    commerce.completeLottery(order, System.currentTimeMillis());
                } else {
                    commerce.setPurchaseState(id, touched.get() ? "PAYMENT_UNKNOWN" : "FAILED", failure.name());
                    if (touched.get())
                        inbox.setDeliveryState(type.entitlement(id), "PAYMENT_UNKNOWN");
                    else inbox.removeDelivery(type.entitlement(id));
                }
                return receipt(order);
            });
            if (error == null) result.complete(committed);
            else result.completeExceptionally(new AgentException(failure, error));
            return null;
        }).exceptionally(failure -> {
            if (AgentException.failure(failure).equals(AgentFailure.CANCELLED)) {
                result.completeExceptionally(new AgentException(AgentFailure.CANCELLED, failure));
                return null;
            }
            SystemMod.LOGGER.error("Commerce order finalization failed for player {} order {}", context.player(), id, failure);
            result.completeExceptionally(new AgentException(touched.get() ? AgentFailure.EFFECT_UNKNOWN : AgentFailure.STORAGE_UNAVAILABLE, failure));
            return null;
        }));
        return result.whenComplete((ignored, error) -> this.active.remove(key));
    }

    private static PurchaseReceiptOutput receipt(Purchase purchase) {
        return new PurchaseReceiptOutput(purchase.id, purchase.order.type().entitlement(purchase.id), purchase.order.reward(), purchase.state,
                purchase.order.type() == PurchaseType.LOTTERY ? Optional.of(purchase.order.source()) : Optional.empty(), purchase.order.trade());
    }

}
