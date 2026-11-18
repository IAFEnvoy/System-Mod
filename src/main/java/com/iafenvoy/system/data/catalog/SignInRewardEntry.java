package com.iafenvoy.system.data.catalog;

import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.iafenvoy.system.data.space.SpaceStack;
import com.iafenvoy.system.data.store.Reward;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.List;
import java.util.Optional;

/**
 * One sign-in reward table entry; the id comes from the registry key, not from the payload.
 */
public record SignInRewardEntry(Component label, List<ItemStackTemplate> items, List<SpaceStack> contents,
                                boolean enabled, EntryConditions conditions,
                                Optional<Double> offlineValue) implements ConditionalEntry {
    public static final Codec<SignInRewardEntry> CODEC = RecordCodecBuilder.<SignInRewardEntry>create(i -> i.group(
            MiscCodecs.TRANSLATABLE_COMPONENT.optionalFieldOf("label", Component.empty()).forGetter(SignInRewardEntry::label),
            Reward.ITEMS_CODEC.optionalFieldOf("items", List.of()).forGetter(SignInRewardEntry::items),
            Reward.CONTENTS_CODEC.optionalFieldOf("contents", List.of()).forGetter(SignInRewardEntry::contents),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(SignInRewardEntry::enabled),
            EntryConditions.CODEC.forGetter(SignInRewardEntry::conditions),
            MiscCodecs.POSITIVE_DOUBLE.optionalFieldOf("offline_value").forGetter(SignInRewardEntry::offlineValue)
    ).apply(i, SignInRewardEntry::new)).validate(entry -> entry.enabled && (entry.label.getString().isBlank() || entry.items.isEmpty() && entry.contents.isEmpty())
            ? DataResult.error(() -> "Enabled sign-in rewards need a label and reward contents") : DataResult.success(entry));

    public SignInRewardEntry {
        items = List.copyOf(items);
        contents = List.copyOf(contents);
    }

    public PersistHolder<Reward> reward(Identifier id) {
        return new PersistHolder<>(id, new Reward(this.label, this.items, this.contents));
    }
}
