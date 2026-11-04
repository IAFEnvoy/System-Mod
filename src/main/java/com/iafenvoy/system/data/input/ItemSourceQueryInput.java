package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.AgentAction;
import com.iafenvoy.system.itemsource.ItemSourceProviders;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;

import java.util.Locale;

/**
 * Built-in parameters with boundary validation.
 *
 * @param scope narrows the lookup to one provider, e.g. {@code recipe}, {@code loot} or {@code tag};
 *              {@link ItemSourceProviders#ALL} keeps the complete query
 */
public record ItemSourceQueryInput(String itemId, String scope) implements AgentAction {
    public static final SchemaMapCodec<ItemSourceQueryInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("item_id", SchemaCodecs.STRING.described("Item registry ID, e.g. minecraft:diamond_pickaxe.")
                            .validate(value -> value.isBlank() ? DataResult.error(() -> "Blank item ID") : DataResult.success(value)))
                    .forGetter(ItemSourceQueryInput::itemId),
            f.optional("scope", SchemaCodecs.STRING, ItemSourceProviders.ALL).forGetter(ItemSourceQueryInput::scope)
    ).apply(i, ItemSourceQueryInput::new));

    public ItemSourceQueryInput {
        scope = scope == null || scope.isBlank() ? ItemSourceProviders.ALL : scope.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public MapCodec<ItemSourceQueryInput> codec() {
        return CODEC;
    }
}
