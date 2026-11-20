package com.iafenvoy.system.data.space;

import com.iafenvoy.system.data.store.Reward;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/**
 * The ticket is the source entry's remaining quantity before selecting one unit. Never evicted.
 */
public record ChoiceReceipt(String entry, long ticket, Component name, int selected, Reward reward, long created) {
    public static final Codec<ChoiceReceipt> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("entry").forGetter(ChoiceReceipt::entry),
            Codec.LONG.fieldOf("ticket").forGetter(ChoiceReceipt::ticket),
            ComponentSerialization.CODEC.fieldOf("name").forGetter(ChoiceReceipt::name),
            Codec.intRange(0, 35).fieldOf("selected").forGetter(ChoiceReceipt::selected),
            Reward.SNAPSHOT_CODEC.codec().fieldOf("reward").forGetter(ChoiceReceipt::reward),
            Codec.LONG.fieldOf("created").forGetter(ChoiceReceipt::created)
    ).apply(i, ChoiceReceipt::new));

    public ChoiceReceipt {
        name = name.copy();
    }

    public static String key(String entry, long ticket) {
        return entry + ":" + ticket;
    }
}
