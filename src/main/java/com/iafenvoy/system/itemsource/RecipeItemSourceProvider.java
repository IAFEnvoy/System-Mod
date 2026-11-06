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
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the server recipe manager; no client classes and no JEI are required.
 * <p>
 * Only recipes that implement {@link Recipe#display()} can be described: that method returns an empty list by default,
 * so special recipes such as item repair or map extension stay invisible here, and brewing is not a {@link Recipe} at
 * all. The category-based projection that fills those gaps lives on the client, in JEI.
 */
public final class RecipeItemSourceProvider implements ItemSourceProvider {
    @Override
    public Identifier id() {
        return Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "recipe");
    }

    @Override
    public List<ItemSource> find(ServerLevel level, Item item) {
        ContextMap context = SlotDisplayContext.fromLevel(level);
        List<ItemSource> sources = new ArrayList<>();
        for (RecipeHolder<?> holder : level.getServer().getRecipeManager().recipeMap().values()) {
            try {
                sources.addAll(describe(holder, context, item));
            } catch (RuntimeException error) {
                SystemMod.LOGGER.warn("Unable to describe item source recipe {}", holder.id().identifier(), error);
            }
        }
        return sources;
    }

    private static List<ItemSource> describe(RecipeHolder<?> holder, ContextMap context, Item item) {
        Recipe<?> recipe = holder.value();
        Identifier type = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        List<ItemSource> sources = new ArrayList<>();
        for (RecipeDisplay display : recipe.display()) {
            List<ItemStack> results = display.result().resolveForStacks(context).stream().filter(stack -> stack.is(item)).toList();
            if (results.isEmpty()) continue;
            // Each display is a separate recipe variant; never merge different patterns into one material list.
            List<SlotDisplay> ingredients = switch (display) {
                case ShapedCraftingRecipeDisplay shaped -> shaped.ingredients();
                case ShapelessCraftingRecipeDisplay shapeless -> shapeless.ingredients();
                case FurnaceRecipeDisplay furnace -> List.of(furnace.ingredient());
                case StonecutterRecipeDisplay stonecutter -> List.of(stonecutter.input());
                case SmithingRecipeDisplay smithing ->
                        List.of(smithing.template(), smithing.base(), smithing.addition());
                default -> recipe.placementInfo().ingredients().stream().map(Ingredient::display).toList();
            };
            List<ItemSourceSlot> inputs = ingredients.stream().map(slot -> ItemSourceSlot.ofStacks(slot.resolveForStacks(context))).toList();
            List<String> station = display.craftingStation().resolveForStacks(context).stream()
                    .map(ItemSourceIds::of).filter(id -> !id.isEmpty()).distinct().toList();
            int width = display instanceof ShapedCraftingRecipeDisplay shaped ? shaped.width() : 0;
            List<String> notes = display instanceof FurnaceRecipeDisplay && recipe.getType() != RecipeType.CAMPFIRE_COOKING
                    ? List.of("requires_fuel") : List.of();
            sources.add(new ItemSource("recipe", holder.id().identifier().toString(), type == null ? "" : type.toString(),
                    station, inputs, List.of(ItemSourceSlot.ofStacks(results)), notes, width));
        }
        return sources;
    }
}
