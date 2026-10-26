package com.iafenvoy.system.runtime.reload;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.commerce.CatalogEntry;
import com.iafenvoy.system.agent.commerce.CommerceCatalog;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.config.AgentSettings;
import com.iafenvoy.system.util.CodecIO;
import com.mojang.serialization.Codec;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.resource.ListenerKey;
import org.jspecify.annotations.NonNull;

/**
 * Retained per resource reload; failed reloads never publish a partial catalog.
 */
public final class CatalogReloadListener extends SimplePreparableReloadListener<CatalogReloadListener.Definitions> {
    public static final ListenerKey<CatalogReloadListener> KEY = ListenerKey.create(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "commerce_catalog"));
    private Definitions definitions;

    @Override
    protected Definitions prepare(ResourceManager manager, @NonNull ProfilerFiller profiler) {
        Map<Identifier, Resource> rewardFiles = manager.listResources("system/signin_rewards", id -> id.getPath().endsWith(".json"));
        Map<Identifier, Resource> offerFiles = manager.listResources("system/shop_offers", id -> id.getPath().endsWith(".json"));
        if (rewardFiles.size() > 64 || offerFiles.size() > 512)
            throw new IllegalArgumentException("Too many commerce resources");
        List<PlayerData.Reward> rewards = new ArrayList<>();
        List<CommerceCatalog.Offer> offers = new ArrayList<>();
        rewardFiles.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            CatalogEntry value = read(entry, CatalogEntry.REWARD);
            if (value.enabled()) rewards.add(new PlayerData.Reward(id(entry.getKey(), "system/signin_rewards/"), value.label(), value.items()));
        });
        offerFiles.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            CatalogEntry value = read(entry, CatalogEntry.OFFER);
            if (value.enabled()) offers.add(new CommerceCatalog.Offer(id(entry.getKey(), "system/shop_offers/"), value.label(), value.items(),
                    value.price().orElseThrow(() -> new IllegalArgumentException("Missing offer price: " + entry.getKey()))));
        });
        CommerceCatalog.create(rewards, offers);
        for (PlayerData.Reward reward : rewards) validateItems(reward.items(), true);
        for (CommerceCatalog.Offer offer : offers) {
            validateItems(offer.items(), true);
            if (offer.price().type().equals("ITEMS")) validateItems(offer.price().items(), false);
        }
        return new Definitions(!rewardFiles.isEmpty(), !offerFiles.isEmpty(), List.copyOf(rewards), List.copyOf(offers));
    }

    @Override
    protected void apply(Definitions prepared, @NonNull ResourceManager manager, @NonNull ProfilerFiller profiler) {
        this.definitions = prepared;
    }

    public CommerceCatalog catalog(AgentSettings fallback) {
        if (this.definitions == null) throw new IllegalStateException("Catalog has not loaded");
        CommerceCatalog result = CommerceCatalog.create(this.definitions.rewardOverride ? this.definitions.rewards : fallback.rewards,
                this.definitions.offerOverride ? this.definitions.offers : fallback.shopOffers);
        for (PlayerData.Reward reward : result.rewards()) validateItems(reward.items(), true);
        for (CommerceCatalog.Offer offer : result.offers()) {
            validateItems(offer.items(), true);
            if (offer.price().type().equals("ITEMS")) validateItems(offer.price().items(), false);
        }
        return result;
    }

    private static <T> T read(Map.Entry<Identifier, Resource> entry, Codec<T> codec) {
        try (InputStream input = entry.getValue().open()) {
            byte[] bytes = input.readNBytes(16385);
            if (bytes.length > 16384) throw new IllegalArgumentException("Resource too large");
            return CodecIO.parse(codec, new String(bytes, StandardCharsets.UTF_8), 16384);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid commerce resource " + entry.getKey(), e);
        }
    }

    private static String id(Identifier resource, String prefix) {
        String path = resource.getPath();
        return resource.getNamespace() + ":" + path.substring(prefix.length(), path.length() - 5);
    }

    public static void validateItems(List<PlayerData.Item> items, boolean output) {
        for (PlayerData.Item item : items) {
            Identifier id = Identifier.tryParse(item.id());
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id))
                throw new IllegalArgumentException("Unknown item " + item.id());
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
            if (stack.isEmpty() || (output && item.count() > stack.getMaxStackSize()))
                throw new IllegalArgumentException("Invalid stack " + item.id());
        }
    }

    public record Definitions(boolean rewardOverride, boolean offerOverride, List<PlayerData.Reward> rewards,
                              List<CommerceCatalog.Offer> offers) {
    }
}
