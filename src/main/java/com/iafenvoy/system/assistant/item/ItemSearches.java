package com.iafenvoy.system.assistant.item;

import com.iafenvoy.evaluate.api.ItemValues;
import com.iafenvoy.evaluate.api.ValueKey;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.execution.AgentEngine;
import com.iafenvoy.assistant.action.AgentAction;
import com.iafenvoy.system.data.catalog.ShopTrade;
import com.iafenvoy.system.data.item.ItemSearchBatch;
import com.iafenvoy.system.data.item.ItemSearchQuery;
import com.iafenvoy.system.data.space.ItemSpaceContent;
import com.iafenvoy.system.data.space.SpaceStack;
import com.iafenvoy.system.util.CodecIO;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * The client chooses selectors; the server owns validity, prices, progression and transaction contents.
 */
public final class ItemSearches implements AutoCloseable {
    private final ServerItemSearchCache cache = new ServerItemSearchCache();

    public ItemSearches(AgentEngine engine) {
        engine.lifecycle((player, _) -> this.cache.clear(player));
    }

    public AgentAction bind(ServerPlayer player, AgentAction action, Optional<ItemSearchBatch> suggestions) {
        if (suggestions.isEmpty()) return action;
        if (!(action instanceof ItemSearchQuery query) || query.searchRef().isPresent())
            throw new AgentException(AgentFailure.INVALID_INPUT);
        ItemSearchBatch batch = suggestions.get();
        if (query.searchOffset() != batch.offset() || !ItemSearchBatch.normalize(query.query()).equals(batch.query()))
            throw new AgentException(AgentFailure.INVALID_INPUT);
        Set<ItemStackTemplate> unique = new HashSet<>();
        for (ItemStackTemplate item : batch.items()) {
            if (item.count() != 1 || !unique.add(item) || !valid(player, item.create()))
                throw new AgentException(AgentFailure.INVALID_INPUT);
        }
        return query.withSearchRef(this.cache.put(player.getUUID(), batch));
    }

    public Optional<ItemSearchBatch> candidates(UUID player, ItemSearchQuery query) {
        return query.searchRef().map(reference -> this.cache.require(player, reference, query.query(), query.searchOffset()));
    }

    public static boolean matches(Set<ItemStackTemplate> candidates, List<ItemStackTemplate> items, List<SpaceStack> contents) {
        return !candidates.isEmpty() && (items.stream().anyMatch(item -> candidates.contains(item.withCount(1)))
                || contents.stream().anyMatch(content -> content.content() instanceof ItemSpaceContent(
                ItemStackTemplate item
        )
                && candidates.contains(item.withCount(1))));
    }

    public static boolean valid(ServerPlayer player, ItemStack stack) {
        return !stack.isEmpty() && stack.getCount() == 1 && ItemStack.validateStrict(stack).isSuccess()
                && stack.getItem().isEnabled(player.level().enabledFeatures());
    }

    /**
     * Wildcard value overrides must never make arbitrary client-written components purchasable.
     */
    public static boolean knownVariant(ServerPlayer player, ItemStack stack, ShopTrade trade) {
        if (!valid(player, stack)) return false;
        if (ItemStack.isSameItemSameComponents(stack, new ItemStack(stack.getItem()))) return true;
        if (ItemValues.all(player.level().getServer()).containsKey(ValueKey.ItemValue.of(stack))) return true;
        if (trade == ShopTrade.BUYBACK)
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
                if (ItemStack.isSameItemSameComponents(stack, player.getInventory().getItem(slot))) return true;
        return false;
    }

    public Identifier remember(ServerPlayer player, String prefix, String namespace, ItemStackTemplate item) {
        String encoded = CodecIO.write(CodecIO.contextual(ItemStackTemplate.CODEC, player.registryAccess()), item);
        String hash = UUID.nameUUIDFromBytes(encoded.getBytes(StandardCharsets.UTF_8)).toString();
        Identifier offer = Identifier.fromNamespaceAndPath(namespace, prefix + hash);
        this.cache.remember(player.getUUID(), offer, item);
        return offer;
    }

    public Optional<ItemStackTemplate> resolve(UUID player, Identifier offer) {
        return this.cache.resolve(player, offer);
    }

    public void clear(UUID player) {
        this.cache.clear(player);
    }

    @Override
    public void close() {
        this.cache.close();
    }
}
