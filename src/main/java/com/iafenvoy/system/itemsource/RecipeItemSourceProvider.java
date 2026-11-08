package com.iafenvoy.system.itemsource;

import com.iafenvoy.system.SystemMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reads the server recipe manager; no client classes and no JEI are required.
 * <p>
 * 1.21.1 recipes expose one result and one ingredient list instead of 26.1's display variants, so every recipe is
 * described once: its ingredients, its station and the result. Recipes without a result item or with only empty
 * ingredients stay invisible here, and brewing is not a {@link Recipe} at all.
 */
public final class RecipeItemSourceProvider implements ItemSourceProvider {
    private static final Set<String> FUEL_TYPES = Set.of("smelting", "blasting", "smoking");

    @Override
    public ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath(SystemMod.MOD_ID, "recipe");
    }

    @Override
    public List<ItemSource> find(ServerLevel level, Item item) {
        List<ItemSource> sources = new ArrayList<>();
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            try {
                sources.addAll(describe(level, holder, item));
            } catch (RuntimeException error) {
                SystemMod.LOGGER.warn("Unable to describe item source recipe {}", holder.id(), error);
            }
        }
        return sources;
    }

    private static List<ItemSource> describe(ServerLevel level, RecipeHolder<?> holder, Item item) {
        Recipe<?> recipe = holder.value();
        ItemStack result = recipe.getResultItem(level.registryAccess());
        if (result.isEmpty() || !result.is(item)) return List.of();
        List<ItemSourceSlot> inputs = recipe.getIngredients().stream()
                .filter(ingredient -> !ingredient.isEmpty())
                .map(ingredient -> ItemSourceSlot.ofStacks(List.of(ingredient.getItems())))
                .toList();
        ResourceLocation type = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        String path = type == null ? "" : type.getPath();
        List<String> station = station(path);
        List<String> notes = FUEL_TYPES.contains(path) ? List.of("requires_fuel") : List.of();
        int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 0;
        return List.of(new ItemSource("recipe", holder.id().toString(), type == null ? "" : type.toString(),
                station, inputs, List.of(ItemSourceSlot.ofStacks(List.of(result))), notes, width));
    }

    private static List<String> station(String path) {
        String id = switch (path) {
            case "crafting", "crafting_shaped", "crafting_shapeless", "crafting_special_armordye",
                 "crafting_special_bookcloning", "crafting_special_firework_rocket",
                 "crafting_special_firework_star", "crafting_special_firework_star_fade",
                 "crafting_special_mapcloning", "crafting_special_mapextending",
                 "crafting_special_repairitem", "crafting_special_shielddecoration",
                 "crafting_special_shulkerboxcoloring", "crafting_special_suspiciousstew",
                 "crafting_special_tippedarrow", "crafting_special_bannerduplicate",
                 "crafting_decorated_pot", "crafting_transmute" -> "minecraft:crafting_table";
            case "smelting" -> "minecraft:furnace";
            case "blasting" -> "minecraft:blast_furnace";
            case "smoking" -> "minecraft:smoker";
            case "campfire_cooking" -> "minecraft:campfire";
            case "stonecutting" -> "minecraft:stonecutter";
            case "smithing", "smithing_transform", "smithing_trim" -> "minecraft:smithing_table";
            default -> "";
        };
        return id.isEmpty() ? List.of() : List.of(id);
    }
}