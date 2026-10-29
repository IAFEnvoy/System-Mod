package com.iafenvoy.system.util;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult.Error;
import net.minecraft.util.Unit;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * A stack description that can be parsed before the item-component registry exists.
 *
 * <p>Minecraft 26.1 ships this type as {@code net.minecraft.world.item.ItemStackTemplate}; 1.21.1 does not have it, so
 * the datapack-facing model keeps the same shape here. A template stores the holder, the count and the component patch,
 * and only binds into a real {@link ItemStack} when {@link #create()} or {@link #apply} runs.
 */
public record ItemStackTemplate(Holder<Item> item, int count, DataComponentPatch components) {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<ItemStackTemplate> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                            BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("id").forGetter(ItemStackTemplate::item),
                            ExtraCodecs.intRange(1, 99).optionalFieldOf("count", 1).forGetter(ItemStackTemplate::count),
                            DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(ItemStackTemplate::components)
                    )
                    .apply(instance, ItemStackTemplate::new)
    );
    public static final Codec<ItemStackTemplate> CODEC = Codec.withAlternative(MAP_CODEC.codec(), BuiltInRegistries.ITEM.holderByNameCodec(), ItemStackTemplate::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemStackTemplate> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.holderRegistry(Registries.ITEM), ItemStackTemplate::item,
            ByteBufCodecs.VAR_INT, ItemStackTemplate::count,
            DataComponentPatch.STREAM_CODEC, ItemStackTemplate::components,
            ItemStackTemplate::new
    );

    public ItemStackTemplate(Item item) {
        this(item.builtInRegistryHolder(), 1, DataComponentPatch.EMPTY);
    }

    public ItemStackTemplate(Item item, int count) {
        this(item.builtInRegistryHolder(), count, DataComponentPatch.EMPTY);
    }

    public ItemStackTemplate(Item item, DataComponentPatch patch) {
        this(item.builtInRegistryHolder(), 1, patch);
    }

    public ItemStackTemplate(Item item, int count, DataComponentPatch patch) {
        this(item.builtInRegistryHolder(), count, patch);
    }

    public ItemStackTemplate(Holder<Item> item) {
        this(item, 1, DataComponentPatch.EMPTY);
    }

    public ItemStackTemplate(Holder<Item> item, int count) {
        this(item, count, DataComponentPatch.EMPTY);
    }

    public ItemStackTemplate(Holder<Item> item, DataComponentPatch patch) {
        this(item, 1, patch);
    }

    public ItemStackTemplate {
        if (count == 0 || item.is(Items.AIR.builtInRegistryHolder()))
            throw new IllegalStateException("Item must be non-empty");
    }

    public static ItemStackTemplate fromNonEmptyStack(ItemStack stack) {
        if (stack.isEmpty()) throw new IllegalStateException("Stack must be non-empty");
        return new ItemStackTemplate(stack.getItemHolder(), stack.getCount(), stack.getComponentsPatch());
    }

    public ItemStackTemplate withCount(int count) {
        return this.count == count ? this : new ItemStackTemplate(this.item, count, this.components);
    }

    public ItemStack create() {
        return this.validate(new ItemStack(this.item, this.count, this.components));
    }

    public ItemStack apply(DataComponentPatch additionalPatch) {
        return this.apply(this.count, additionalPatch);
    }

    public ItemStack apply(int count, DataComponentPatch additionalPatch) {
        ItemStack result = new ItemStack(this.item, count, additionalPatch);
        result.applyComponents(this.components);
        return this.validate(result);
    }

    private ItemStack validate(ItemStack result) {
        // 1.21.1 keeps ItemStack.validateStrict private, so the same two checks are spelled out here.
        Optional<Error<Unit>> componentError = ItemStack.validateComponents(result.getComponents()).error();
        if (componentError.isPresent()) {
            LOGGER.warn("Can't create item stack with properties {}, error: {}", this, componentError.get().message());
            return ItemStack.EMPTY;
        }
        if (result.getCount() > result.getMaxStackSize()) {
            LOGGER.warn("Can't create item stack with properties {}: count {} exceeds maximum {}",
                    this, result.getCount(), result.getMaxStackSize());
            return ItemStack.EMPTY;
        }
        return result;
    }
}
