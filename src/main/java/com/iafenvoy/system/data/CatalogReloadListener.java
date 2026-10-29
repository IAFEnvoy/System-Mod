package com.iafenvoy.system.data;

import com.google.gson.JsonParser;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.commerce.CatalogEntry;
import com.iafenvoy.system.agent.commerce.CommerceCatalog;
import com.iafenvoy.system.agent.commerce.LotteryPool;
import com.iafenvoy.system.agent.store.PlayerData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Retained per resource reload; failed reloads never publish a partial catalog.
 */
public final class CatalogReloadListener extends SimplePreparableReloadListener<CommerceCatalog> {
    // 1.21.1 has no keyed retained-listener registry, so the last successfully applied instance publishes itself.
    private static volatile CatalogReloadListener latest;
    private CommerceCatalog catalog;

    @Override
    protected CommerceCatalog prepare(ResourceManager manager, @NotNull ProfilerFiller profiler) {
        Map<ResourceLocation, Resource> rewardFiles = manager.listResources("system/signin_rewards", id -> id.getPath().endsWith(".json"));
        Map<ResourceLocation, Resource> offerFiles = manager.listResources("system/shop_offers", id -> id.getPath().endsWith(".json"));
        Map<ResourceLocation, Resource> lotteryFiles = manager.listResources("system/lottery_pools", id -> id.getPath().endsWith(".json"));
        List<PlayerData.Reward> rewards = new ArrayList<>();
        List<CommerceCatalog.Offer> offers = new ArrayList<>();
        List<LotteryPool> lotteries = new ArrayList<>();
        rewardFiles.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            CatalogEntry value = this.read(entry, CatalogEntry.REWARD);
            if (value.enabled())
                rewards.add(new PlayerData.Reward(id(entry.getKey(), "system/signin_rewards/"), value.label(), value.items()));
        });
        offerFiles.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            CatalogEntry value = this.read(entry, CatalogEntry.OFFER);
            if (value.enabled())
                offers.add(new CommerceCatalog.Offer(id(entry.getKey(), "system/shop_offers/"), value.label(), value.items(),
                        value.costs().orElseThrow(() -> new IllegalArgumentException("Missing offer costs: " + entry.getKey()))));
        });
        lotteryFiles.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            LotteryPool.Definition value = this.read(entry, LotteryPool.Definition.CODEC);
            if (value.enabled()) lotteries.add(value.resolve(id(entry.getKey(), "system/lottery_pools/")));
        });
        // Template codecs validate item definitions before item components have been bound.
        return CommerceCatalog.create(rewards, offers, lotteries);
    }

    @Override
    protected void apply(CommerceCatalog prepared, @NotNull ResourceManager manager, @NotNull ProfilerFiller profiler) {
        this.catalog = prepared;
        latest = this;
    }

    /**
     * Returns the catalog installed by the most recent successful reload, or {@code null} before the first one.
     */
    public static CatalogReloadListener latest() {
        return latest;
    }

    public CommerceCatalog catalog() {
        if (this.catalog == null) throw new IllegalStateException("Catalog has not loaded");
        return this.catalog;
    }

    private <T> T read(Map.Entry<ResourceLocation, Resource> entry, Codec<T> codec) {
        try (InputStream input = entry.getValue().open()) {
            byte[] bytes = input.readAllBytes();
            return codec.parse(this.getRegistryLookup().createSerializationContext(JsonOps.INSTANCE),
                    JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8))).getOrThrow();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid commerce resource " + entry.getKey(), e);
        }
    }

    private static ResourceLocation id(ResourceLocation resource, String prefix) {
        String path = resource.getPath();
        return resource.withPath(path.substring(prefix.length(), path.length() - 5));
    }

}
