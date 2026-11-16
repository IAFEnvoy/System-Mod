package com.iafenvoy.system.data.space;

import com.iafenvoy.saturn.util.HolderHelper;
import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.system.data.catalog.CurrencyDefinition;
import com.iafenvoy.system.registry.SystemSpaceContentTypes;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

/**
 * Decode a registry reference, but persist metadata so removing a currency never breaks the player's file.
 */
public record CurrencySpaceContent(Identifier id, Holder<CurrencyDefinition> currency) implements SpaceContent {
    private static final Codec<PersistHolder<CurrencyDefinition>> SNAPSHOT = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("id").forGetter(PersistHolder::id),
            CurrencyDefinition.DIRECT_CODEC.fieldOf("definition").forGetter(PersistHolder::value)
    ).apply(i, PersistHolder::new));
    public static final MapCodec<CurrencySpaceContent> CODEC = Codec.either(CurrencyDefinition.CODEC, SNAPSHOT)
            .xmap(value -> value.map(CurrencySpaceContent::new, saved -> new CurrencySpaceContent(saved.id(), Holder.direct(saved.value()))),
                    content -> Either.right(new PersistHolder<>(content.id, content.currency.value())))
            .fieldOf("currency");

    public CurrencySpaceContent(Holder<CurrencyDefinition> currency) {
        // Registry loading is parallel: the key exists before the referenced definition is bound.
        this(HolderHelper.id(currency), currency);
    }

    @Override
    public boolean equals(Object other) {
        // A live reference and a persisted snapshot of the same currency share one balance and space group.
        return other instanceof CurrencySpaceContent content && this.id.equals(content.id);
    }

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }

    @Override
    public SpaceContentType<CurrencySpaceContent> type() {
        return SystemSpaceContentTypes.CURRENCY.get();
    }
}
