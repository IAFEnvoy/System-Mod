package com.iafenvoy.system.data.catalog;

import com.iafenvoy.system.data.EnabledEntry;
import com.iafenvoy.system.data.store.Reward;
import com.iafenvoy.system.util.PersistHolder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.List;

/**
 * One sign-in reward table entry; the id comes from the registry key, not from the payload.
 */
public record SignInRewardEntry(Component label, List<ItemStackTemplate> items, boolean enabled) implements EnabledEntry {
    public static final Codec<SignInRewardEntry> CODEC = RecordCodecBuilder.<SignInRewardEntry>create(i -> i.group(
            ComponentSerialization.CODEC.optionalFieldOf("label", Component.empty()).forGetter(SignInRewardEntry::label),
            Reward.ITEMS_CODEC.optionalFieldOf("items", List.of()).forGetter(SignInRewardEntry::items),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(SignInRewardEntry::enabled)
    ).apply(i, SignInRewardEntry::new)).validate(entry -> entry.enabled && (entry.label.getString().isBlank() || entry.items.isEmpty())
            ? DataResult.error(() -> "Enabled sign-in rewards need a label and items") : DataResult.success(entry));

    public SignInRewardEntry {
        items = List.copyOf(items);
    }

    public PersistHolder<Reward> reward(Identifier id) {
        return new PersistHolder<>(id, new Reward(this.label, this.items));
    }
}
