package com.iafenvoy.saturn.data.information;

import com.iafenvoy.saturn.data.information.model.ViewComponents;
import com.iafenvoy.saturn.registry.SaturnResourceKeys;
import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.iafenvoy.saturn.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.TagKey;

import java.util.Comparator;
import java.util.List;

/**
 * Groups own membership; each entry selects one registered row or an entire row tag.
 */
public record InformationGroup(Component title, int order,
                               List<Either<Holder<InformationRow>, TagKey<InformationRow>>> rows) {
    public static final Codec<Holder<InformationGroup>> CODEC = RegistryFixedCodec.create(SaturnResourceKeys.INFORMATION_GROUP);
    public static final Codec<InformationGroup> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            MiscCodecs.TRANSLATABLE_COMPONENT.fieldOf("title").forGetter(InformationGroup::title),
            Codec.INT.optionalFieldOf("order", 0).forGetter(InformationGroup::order),
            RegistryCodecs.holderOrTag(SaturnResourceKeys.INFORMATION_ROW).listOf().fieldOf("rows").forGetter(InformationGroup::rows)
    ).apply(i, InformationGroup::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<InformationGroup>> STREAM_CODEC = ByteBufCodecs.holderRegistry(SaturnResourceKeys.INFORMATION_GROUP);
    public static final Comparator<Holder<InformationGroup>> ORDER = Comparator
            .comparingInt((Holder<InformationGroup> group) -> group.value().order())
            .thenComparing(group -> group.unwrapKey().orElseThrow().location());

    public InformationGroup {
        title = ViewComponents.copy(title);
        rows = rows.stream().distinct().toList();
    }

    @Override
    public Component title() {
        return ViewComponents.copy(this.title);
    }
}
