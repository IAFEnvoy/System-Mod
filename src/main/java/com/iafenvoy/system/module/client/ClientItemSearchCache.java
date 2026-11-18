package com.iafenvoy.system.module.client;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.item.ItemSearchBatch;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.SessionSearchTrees;
import net.minecraft.client.searchtree.SearchTree;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.CreativeModeTabSearchRegistry;
import net.neoforged.neoforge.client.event.ClientResourceLoadFinishedEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Uses the game's ItemStack tooltip search tree; never waits for index construction on the UI thread.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class ClientItemSearchCache {
    private static final SessionSearchTrees.Key SPACE_NAMES = new SessionSearchTrees.Key();
    private static final SessionSearchTrees.Key SPACE_TAGS = new SessionSearchTrees.Key();
    private ClientPacketListener connection;
    private Language language;
    private ClientPacketListener spaceConnection;
    private Language spaceLanguage;
    private long spaceRevision = -1;
    private long generation;

    public CompletableFuture<ItemSearchBatch> search(String text, int offset) {
        return this.search(text, offset, false);
    }

    public CompletableFuture<ItemSearchBatch> searchStored(String text) {
        return this.search(text, 0, ClientModuleCaches.SPACE.itemRevision() >= 0);
    }

    private CompletableFuture<ItemSearchBatch> search(String text, int offset, boolean stored) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft.getConnection();
        String query = ItemSearchBatch.normalize(text);
        if (connection == null || minecraft.player == null || minecraft.level == null || query.isEmpty()
                || query.length() > 128 || offset < 0 || offset > ItemSearchBatch.MAX_OFFSET)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid item search"));
        try {
            Language language = Language.getInstance();
            long spaceRevision = ClientModuleCaches.SPACE.itemRevision();
            boolean creative = !stored || !ClientModuleCaches.SPACE.hasSnapshot();
            boolean rebuilt = false;
            if (creative) {
                // Partial mutation hints still need the normal catalog until the first full space snapshot.
                rebuilt = CreativeModeTabs.tryRebuildTabContents(minecraft.level.enabledFeatures(), false, connection.registryAccess());
                if (rebuilt || this.connection != connection || this.language != language) {
                    List<ItemStack> items = List.copyOf(CreativeModeTabs.searchTab().getDisplayItems());
                    connection.searchTrees().updateCreativeTooltips(connection.registryAccess(), items);
                    connection.searchTrees().updateCreativeTags(items);
                    this.connection = connection;
                    this.language = language;
                }
            }
            if (stored) {
                if (rebuilt || this.spaceConnection != connection || this.spaceLanguage != language || this.spaceRevision != spaceRevision) {
                    Stream<ItemStack> catalog = creative ? CreativeModeTabs.searchTab().getDisplayItems().stream() : Stream.empty();
                    List<ItemStack> items = Stream.concat(catalog, ClientModuleCaches.SPACE.items().stream().map(ItemStackTemplate::create))
                            .filter(item -> !item.isEmpty()).toList();
                    connection.searchTrees().updateCreativeTooltips(connection.registryAccess(), items, SPACE_NAMES);
                    connection.searchTrees().updateCreativeTags(items, SPACE_TAGS);
                    this.spaceConnection = connection;
                    this.spaceLanguage = language;
                    this.spaceRevision = spaceRevision;
                }
            }
            CompletableFuture<SearchTree<ItemStack>> ready = query.startsWith("#")
                    ? CreativeModeTabSearchRegistry.getTagSearchTree(stored ? SPACE_TAGS : SessionSearchTrees.CREATIVE_TAGS)
                    : CreativeModeTabSearchRegistry.getNameSearchTree(stored ? SPACE_NAMES : SessionSearchTrees.CREATIVE_NAMES);
            CompletableFuture<ItemSearchBatch> result = new CompletableFuture<>();
            long generation = this.generation;
            ready.whenComplete((tree, error) -> minecraft.execute(() -> {
                if (error != null) result.completeExceptionally(error);
                else if (minecraft.getConnection() != connection || Language.getInstance() != language
                        || this.generation != generation || stored && ClientModuleCaches.SPACE.itemRevision() != spaceRevision)
                    result.completeExceptionally(new IllegalStateException("Item search was invalidated"));
                else try {
                        result.complete(batch(connection, tree, query, offset));
                    } catch (RuntimeException failure) {
                        result.completeExceptionally(failure);
                    }
            }));
            return result;
        } catch (RuntimeException error) {
            return CompletableFuture.failedFuture(error);
        }
    }

    private static ItemSearchBatch batch(ClientPacketListener connection, SearchTree<ItemStack> tree, String query, int offset) {
        List<ItemStack> matches = tree.search(query.startsWith("#") ? query.substring(1) : query);
        List<ItemStackTemplate> items = new ArrayList<>();
        Set<ItemStackTemplate> seen = new HashSet<>();
        int index = Math.min(offset, matches.size()), bytes = 0, scanned = 0;
        Identifier exact = query.contains(":") ? Identifier.tryParse(query) : null;
        boolean exactItem = exact != null && BuiltInRegistries.ITEM.containsKey(exact);
        while (index < matches.size() && items.size() < ItemSearchBatch.MAX_ITEMS
                && index < ItemSearchBatch.MAX_OFFSET && scanned++ < ItemSearchBatch.MAX_SCANNED) {
            ItemStack stack = matches.get(index++);
            if (stack.isEmpty() || exactItem && !exact.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) continue;
            ItemStackTemplate item = ItemStackTemplate.fromNonEmptyStack(stack).withCount(1);
            if (!seen.add(item)) continue;
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), connection.registryAccess(), connection.getConnectionType());
            try {
                ItemStackTemplate.STREAM_CODEC.encode(buffer, item);
                int size = buffer.readableBytes();
                if (size > ItemSearchBatch.MAX_STACK_BYTES) continue;
                if (bytes + size > ItemSearchBatch.MAX_BYTES) {
                    index--;
                    break;
                }
                bytes += size;
                items.add(item);
            } catch (RuntimeException error) {
                SystemMod.LOGGER.warn("Unable to encode item search candidate {}", BuiltInRegistries.ITEM.getKey(stack.getItem()), error);
            } finally {
                buffer.release();
            }
        }
        return new ItemSearchBatch(query, offset, Math.max(offset, index), items, index < matches.size() && index < ItemSearchBatch.MAX_OFFSET);
    }

    public void clear() {
        this.generation++;
        this.connection = null;
        this.language = null;
        this.spaceConnection = null;
        this.spaceLanguage = null;
        this.spaceRevision = -1;
    }

    @SubscribeEvent
    public static void resources(ClientResourceLoadFinishedEvent event) {
        ClientModuleCaches.ITEM_SEARCH.clear();
    }

    @SubscribeEvent
    public static void tags(TagsUpdatedEvent.ClientPacketReceived event) {
        ClientModuleCaches.ITEM_SEARCH.clear();
    }
}
