package com.iafenvoy.system.data.item;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Client suggestions only. Length-delimited stacks retain components and bound network decoding work.
 */
public record ItemSearchBatch(String query, int offset, int nextOffset, List<ItemStackTemplate> items,
                              boolean hasMore) {
    public static final int MAX_ITEMS = 32;
    public static final int MAX_SCANNED = 256;
    public static final int MAX_STACK_BYTES = 4096;
    public static final int MAX_BYTES = 24576;
    public static final int MAX_OFFSET = 1000000;
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemSearchBatch> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ItemSearchBatch decode(RegistryFriendlyByteBuf input) {
            String query = input.readUtf(128);
            int offset = input.readVarInt(), nextOffset = input.readVarInt(), count = input.readVarInt();
            if (count < 0 || count > MAX_ITEMS) throw new IllegalArgumentException("Too many search candidates");
            List<ItemStackTemplate> items = new ArrayList<>(count);
            int bytes = 0;
            for (int index = 0; index < count; index++) {
                int size = input.readVarInt();
                if (size < 1 || size > MAX_STACK_BYTES || (bytes += size) > MAX_BYTES)
                    throw new IllegalArgumentException("Search candidates exceed byte limit");
                RegistryFriendlyByteBuf stack = new RegistryFriendlyByteBuf(input.readSlice(size), input.registryAccess(), input.getConnectionType());
                items.add(ItemStackTemplate.STREAM_CODEC.decode(stack));
                if (stack.isReadable()) throw new IllegalArgumentException("Trailing search stack data");
            }
            return new ItemSearchBatch(query, offset, nextOffset, items, input.readBoolean());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf output, ItemSearchBatch batch) {
            output.writeUtf(batch.query, 128);
            output.writeVarInt(batch.offset);
            output.writeVarInt(batch.nextOffset);
            output.writeVarInt(batch.items.size());
            int bytes = 0;
            for (ItemStackTemplate item : batch.items) {
                ByteBuf buffer = output.alloc().buffer();
                try {
                    RegistryFriendlyByteBuf stack = new RegistryFriendlyByteBuf(buffer, output.registryAccess(), output.getConnectionType());
                    ItemStackTemplate.STREAM_CODEC.encode(stack, item);
                    int size = buffer.readableBytes();
                    if (size > MAX_STACK_BYTES || (bytes += size) > MAX_BYTES)
                        throw new IllegalArgumentException("Search candidates exceed byte limit");
                    output.writeVarInt(size);
                    output.writeBytes(buffer);
                } finally {
                    buffer.release();
                }
            }
            output.writeBoolean(batch.hasMore);
        }
    };

    public ItemSearchBatch {
        query = normalize(query);
        if (query.isEmpty() || query.length() > 128 || offset < 0 || nextOffset < offset
                || nextOffset > MAX_OFFSET || nextOffset - offset > MAX_SCANNED
                || items.size() > MAX_ITEMS || items.size() > nextOffset - offset || hasMore && nextOffset == offset)
            throw new IllegalArgumentException("Invalid item search batch");
        items = List.copyOf(items);
    }

    public static String normalize(String query) {
        return query.strip().toLowerCase(Locale.ROOT);
    }
}
