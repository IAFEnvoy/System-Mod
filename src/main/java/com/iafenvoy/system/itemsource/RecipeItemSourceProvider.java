package com.iafenvoy.system.itemsource;

import com.iafenvoy.system.SystemMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Reads the server recipe manager; no client classes and no JEI are required.
 * <p>
 * Only recipes that implement {@link Recipe#display()} can be described: that method returns an empty list by default,
 * so special recipes such as item repair or map extension stay invisible here, and brewing is not a {@link Recipe} at
 * all. The category-based projection that fills those gaps lives on the client, in JEI.
 */
public final class RecipeItemSourceProvider implements ItemSourceProvider {
    private static final int MAX_SLOT_ITEMS = 8;
    private static final int MAX_SLOTS = 16;

    @Override
    public Identifier id() {
        return Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "recipe");
    }

    @Override
    public List<ItemSource> find(ServerLevel level, Item item) {
        ContextMap context = SlotDisplayContext.fromLevel(level);
        List<ItemSource> sources = new ArrayList<>();
        for (RecipeHolder<?> holder : level.getServer().getRecipeManager().recipeMap().values()) {
            ItemSource source = describe(holder, context, item);
            if (source != null) sources.add(source);
        }
        return sources;
    }

    private static ItemSource describe(RecipeHolder<?> holder, ContextMap context, Item item) {
        Recipe<?> recipe = holder.value();
        List<RecipeDisplay> displays = recipe.display();
        if (displays.isEmpty()) return null;
        // Station slots are only resolved for recipes that actually produce the item.
        boolean produces = false;
        for (RecipeDisplay display : displays)
            for (ItemStack stack : display.result().resolveForStacks(context))
                if (stack.is(item)) produces = true;
        if (!produces) return null;

        List<ItemSourceSlot> outputs = new ArrayList<>();
        List<String> station = new ArrayList<>();
        for (RecipeDisplay display : displays) {
            List<ItemStack> stacks = display.result().resolveForStacks(context);
            if (!stacks.isEmpty()) outputs.add(slot(stacks));
            for (ItemStack stack : display.craftingStation().resolveForStacks(context))
                station.add(ItemSourceIds.of(stack));
        }

        List<ItemSourceSlot> inputs = new ArrayList<>();
        for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
            if (inputs.size() == MAX_SLOTS) break;
            List<String> ids = ingredient.display().resolveForStacks(context).stream()
                    .map(ItemSourceIds::of)
                    .filter(id -> !id.isEmpty())
                    .distinct()
                    .sorted()
                    .limit(MAX_SLOT_ITEMS)
                    .toList();
            if (!ids.isEmpty()) inputs.add(new ItemSourceSlot(ids, 1));
        }

        Identifier type = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        return new ItemSource("recipe", holder.id().identifier().toString(), type == null ? "" : type.toString(),
                List.copyOf(new LinkedHashSet<>(station)), inputs, outputs, List.of());
    }

    private static ItemSourceSlot slot(List<ItemStack> stacks) {
        List<String> ids = stacks.stream()
                .map(ItemSourceIds::of)
                .filter(id -> !id.isEmpty())
                .distinct()
                .sorted()
                .limit(MAX_SLOT_ITEMS)
                .toList();
        int count = stacks.stream().mapToInt(ItemStack::getCount).max().orElse(1);
        return new ItemSourceSlot(ids, count);
    }
}
