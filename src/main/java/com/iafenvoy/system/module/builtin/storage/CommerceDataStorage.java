package com.iafenvoy.system.module.builtin.storage;

import com.iafenvoy.saturn.data.storage.DataStorage;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.data.store.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.*;
import java.util.stream.Stream;

/**
 * Purchase journals belong to the commerce feature and survive conversation resets.
 */
public final class CommerceDataStorage extends DataStorage {
    public static final MapCodec<CommerceDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Purchase.CODEC).optionalFieldOf("purchases", Map.of()).forGetter(CommerceDataStorage::getPurchases),
            Codec.unboundedMap(Codec.STRING, PurchaseCounts.CODEC).optionalFieldOf("archivedCounts", Map.of()).forGetter(storage -> storage.archivedCounts),
            Codec.STRING.optionalFieldOf("archiveScope").forGetter(storage -> Optional.of(storage.archiveScope)),
            Codec.unboundedMap(Identifier.CODEC, LotteryProgress.CODEC).optionalFieldOf("lotteryProgress", Map.of()).forGetter(storage -> storage.lotteryProgress),
            Codec.BOOL.optionalFieldOf("lotteryCountsReady", false).forGetter(CommerceDataStorage::lotteryCountsReady)
    ).apply(i, CommerceDataStorage::new));
    private final Map<String, Purchase> purchases;
    private final Map<String, PurchaseCounts> archivedCounts;
    private final String archiveScope;
    private final Map<Identifier, LotteryProgress> lotteryProgress;
    private boolean lotteryCountsReady;

    public CommerceDataStorage() {
        this(Map.of(), Map.of(), Optional.empty(), Map.of(), true);
    }

    private CommerceDataStorage(Map<String, Purchase> purchases, Map<String, PurchaseCounts> archivedCounts, Optional<String> archiveScope, Map<Identifier, LotteryProgress> lotteryProgress, boolean lotteryCountsReady) {
        this.purchases = new LinkedHashMap<>(purchases);
        this.archivedCounts = new LinkedHashMap<>(archivedCounts);
        this.archiveScope = archiveScope.map(UUID::fromString).orElseGet(UUID::randomUUID).toString();
        this.lotteryProgress = new LinkedHashMap<>(lotteryProgress);
        this.lotteryCountsReady = lotteryCountsReady;
    }

    public String archiveScope() {
        return this.archiveScope;
    }

    public Map<String, Purchase> getPurchases() {
        return this.purchases;
    }

    public Purchase getPurchase(String id) {
        return this.purchases.get(id);
    }

    public void putPurchase(String id, Purchase purchase) {
        this.purchases.put(id, purchase);
        this.markDirty();
    }

    public void setPurchaseState(String id, String state, String error) {
        Purchase purchase = this.purchases.get(id);
        if (Objects.equals(purchase.state, state) && Objects.equals(purchase.error, error)) return;
        purchase.state = state;
        purchase.error = error;
        this.markDirty();
    }

    public Stream<Purchase> streamPurchases() {
        return this.purchases.values().stream();
    }

    public int purchaseCount(PurchaseType type, Identifier source, long since) {
        // Pending and uncertain payments keep their reservation; only a known failure releases it.
        long active = this.streamPurchases().filter(purchase -> purchase.order.type() == type
                && purchase.order.source().equals(source) && purchase.created >= since
                && !"FAILED".equals(purchase.state)).mapToLong(purchase -> purchase.order.count()).sum();
        long archived = this.archivedCounts.getOrDefault(type.getSerializedName() + ":" + source, PurchaseCounts.EMPTY).count(since);
        return (int) Math.min(Integer.MAX_VALUE, Math.min(Integer.MAX_VALUE, archived) + active);
    }

    /**
     * Call only after the immutable archive has been durably written, inside the same player transaction.
     */
    public void archivePurchase(String id, long now) {
        Purchase purchase = this.purchases.remove(id);
        if (purchase == null) return;
        if ("COMPLETED".equals(purchase.state)) {
            String key = purchase.order.type().getSerializedName() + ":" + purchase.order.source();
            this.archivedCounts.put(key, this.archivedCounts.getOrDefault(key, PurchaseCounts.EMPTY).add(purchase.created, purchase.order.count(), now));
        }
        this.markDirty();
    }

    public void assertPurchaseCount() {
        if (this.purchases.size() >= 4096) throw new AgentException(AgentFailure.STORAGE_FULL);
    }

    public LotteryProgress progress(Identifier pool) {
        return this.lotteryProgress.getOrDefault(pool, LotteryProgress.EMPTY);
    }

    public void completeLottery(Purchase purchase, long now) {
        if (purchase.order.type() != PurchaseType.LOTTERY) return;
        LotteryProgress progress = this.progress(purchase.order.source());
        this.lotteryProgress.put(purchase.order.source(), new LotteryProgress(purchase.order.lottery().map(LotteryRolls::misses).orElse(progress.misses()),
                now, progress.withWins(purchase.order.rewardCounts()).wins()));
        this.markDirty();
    }

    public boolean lotteryCountsReady() {
        return this.lotteryCountsReady;
    }

    /**
     * Seed old saves once, including settled receipts that already left the bounded journal.
     */
    public void initializeLotteryCounts(Map<Identifier, Map<Identifier, Integer>> archived) {
        if (this.lotteryCountsReady) return;
        this.lotteryProgress.replaceAll((pool, progress) -> new LotteryProgress(progress.misses(), progress.lastDraw(), Map.of()));
        archived.forEach((pool, rewards) -> this.lotteryProgress.put(pool, this.progress(pool).withWins(rewards)));
        this.streamPurchases().filter(purchase -> purchase.order.type() == PurchaseType.LOTTERY && "COMPLETED".equals(purchase.state))
                .forEach(purchase -> this.lotteryProgress.put(purchase.order.source(), this.progress(purchase.order.source()).withWins(purchase.order.rewardCounts())));
        this.lotteryCountsReady = true;
        this.markDirty();
    }

    @Override
    public MapCodec<CommerceDataStorage> codec() {
        return CODEC;
    }

    @Override
    public CommerceDataStorage copy() {
        Map<String, Purchase> copy = new LinkedHashMap<>();
        this.purchases.forEach((key, value) -> copy.put(key, value.copy()));
        return new CommerceDataStorage(copy, this.archivedCounts, Optional.of(this.archiveScope), this.lotteryProgress, this.lotteryCountsReady);
    }
}
