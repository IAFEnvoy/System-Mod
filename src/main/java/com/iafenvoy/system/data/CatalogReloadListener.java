package com.iafenvoy.system.data;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.commerce.CatalogEntry;
import com.iafenvoy.system.agent.commerce.CommerceCatalog;
import com.iafenvoy.system.agent.commerce.LotteryPool;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.util.codec.CodecIO;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.resource.ListenerKey;
import org.jspecify.annotations.NonNull;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Retained per resource reload; failed reloads never publish a partial catalog.
 */
public final class CatalogReloadListener extends SimplePreparableReloadListener<CommerceCatalog> {
    public static final ListenerKey<CatalogReloadListener> KEY = ListenerKey.create(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "commerce_catalog"));
    private CommerceCatalog catalog;

    @Override
    protected CommerceCatalog prepare(ResourceManager manager, @NonNull ProfilerFiller profiler) {
        Map<Identifier, Resource> rewardFiles = manager.listResources("system/signin_rewards", id -> id.getPath().endsWith(".json"));
        Map<Identifier, Resource> offerFiles = manager.listResources("system/shop_offers", id -> id.getPath().endsWith(".json"));
        Map<Identifier, Resource> lotteryFiles = manager.listResources("system/lottery_pools", id -> id.getPath().endsWith(".json"));
        List<PlayerData.Reward> rewards = new ArrayList<>();
        List<CommerceCatalog.Offer> offers = new ArrayList<>();
        List<LotteryPool> lotteries = new ArrayList<>();
        rewardFiles.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            CatalogEntry value = read(entry, CatalogEntry.REWARD);
            if (value.enabled())
                rewards.add(new PlayerData.Reward(id(entry.getKey(), "system/signin_rewards/"), value.label(), value.items()));
        });
        offerFiles.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            CatalogEntry value = read(entry, CatalogEntry.OFFER);
            if (value.enabled())
                offers.add(new CommerceCatalog.Offer(id(entry.getKey(), "system/shop_offers/"), value.label(), value.items(),
                        value.price().orElseThrow(() -> new IllegalArgumentException("Missing offer price: " + entry.getKey()))));
        });
        lotteryFiles.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            LotteryPool.Definition value = read(entry, LotteryPool.Definition.CODEC);
            if (value.enabled()) lotteries.add(value.resolve(id(entry.getKey(), "system/lottery_pools/")));
        });
        // Template codecs validate item definitions before item components have been bound.
        return CommerceCatalog.create(rewards, offers, lotteries);
    }

    @Override
    protected void apply(CommerceCatalog prepared, @NonNull ResourceManager manager, @NonNull ProfilerFiller profiler) {
        this.catalog = prepared;
    }

    public CommerceCatalog catalog() {
        if (this.catalog == null) throw new IllegalStateException("Catalog has not loaded");
        return this.catalog;
    }

    private static <T> T read(Map.Entry<Identifier, Resource> entry, Codec<T> codec) {
        try (InputStream input = entry.getValue().open()) {
            byte[] bytes = input.readAllBytes();
            return CodecIO.parse(codec, new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid commerce resource " + entry.getKey(), e);
        }
    }

    private static Identifier id(Identifier resource, String prefix) {
        String path = resource.getPath();
        return resource.withPath(path.substring(prefix.length(), path.length() - 5));
    }

}
