package com.iafenvoy.saturn.registry.content;

import com.iafenvoy.saturn.Saturn;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.iafenvoy.saturn.util.matcher.ItemMatcher;
import com.iafenvoy.saturn.util.matcher.builtin.*;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The built-in {@code item_matcher_entry_type} entries.
 */
public final class SaturnItemMatchers {
    public static final DeferredRegister<MapCodec<? extends ItemMatcher.Entry>> REGISTRY = DeferredRegister.create(SaturnRegistries.ITEM_MATCHER_ENTRY_TYPE, Saturn.MOD_ID);

    static {
        REGISTRY.register("item", () -> ItemEntry.CODEC);
        REGISTRY.register("tag", () -> TagEntry.CODEC);
        REGISTRY.register("wildcard", () -> WildcardEntry.CODEC);
        REGISTRY.register("regex", () -> RegexEntry.CODEC);
        REGISTRY.register("ingredient", () -> IngredientEntry.CODEC);
    }

    private SaturnItemMatchers() {
    }
}
