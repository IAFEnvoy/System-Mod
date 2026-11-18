package com.iafenvoy.system.assistant.progression;

import com.iafenvoy.evaluate.api.ItemValues;
import com.iafenvoy.evaluate.api.ValueKey;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.progression.ProgressionEstimate;
import com.iafenvoy.system.data.progression.ProgressionSettings;
import com.iafenvoy.system.event.PlayerProgressionEvent;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.neoforged.neoforge.common.NeoForge;

import java.util.*;

/**
 * On-demand server-thread evaluation; no tick scanner or cross-reload value cache.
 */
public final class PlayerProgression {
    private PlayerProgression() {
    }

    public static ProgressionEstimate estimate(ServerPlayer player, ProgressionSettings settings) {
        MinecraftServer server = player.level().getServer();
        if (!server.isSameThread()) throw new IllegalStateException("Progression reads require the server thread");
        Map<ValueKey.ItemValue, Double> samples = new HashMap<>();
        Set<ValueKey.ItemValue> visited = new HashSet<>();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++)
            add(server, inventory.getItem(slot), visited, samples);
        int inventorySamples = samples.size();
        if (settings.recipes()) {
            for (ResourceKey<Recipe<?>> id : player.getRecipeBook().pack().known()) {
                Optional<RecipeHolder<?>> holder = server.getRecipeManager().byKey(id);
                if (holder.isEmpty() || holder.get().value().isSpecial()) continue;
                try {
                    for (RecipeDisplay display : holder.get().value().display()) {
                        if (display.result() instanceof SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate item))
                            add(server, item.create(), visited, samples);
                        else if (display.result() instanceof SlotDisplay.ItemSlotDisplay(Holder<Item> item))
                            add(server, new ItemStack(item), visited, samples);
                    }
                } catch (RuntimeException error) {
                    SystemMod.LOGGER.warn("Unable to read progression output for unlocked recipe {}", id.identifier(), error);
                }
            }
        }
        // A single valuable item cannot establish a progression tier by itself.
        double observed = samples.size() < 2 ? 0 : percentile(new ArrayList<>(samples.values()), settings.percentile());
        PlayerProgressionEvent event = new PlayerProgressionEvent(player, observed, samples.size());
        NeoForge.EVENT_BUS.post(event);
        double value = event.value();
        // No observations means a bootstrap window, still bounded by any authoritative stage.
        double lower = event.clamp(Math.min(Double.MAX_VALUE, value * settings.lowerFactor()));
        double upper = event.clamp(value > 0 ? Math.min(Double.MAX_VALUE, value * settings.upperFactor()) : Double.MAX_VALUE);
        int reliableSamples = (int) Math.ceil(1 / (1 - settings.percentile()) - 1.0e-9);
        ProgressionEstimate.Confidence confidence = samples.size() < reliableSamples ? ProgressionEstimate.Confidence.LOW
                : event.calibrated() ? ProgressionEstimate.Confidence.HIGH : ProgressionEstimate.Confidence.MEDIUM;
        return new ProgressionEstimate(value, lower, upper, event.minimum(), samples.size(), samples.size() - inventorySamples,
                settings.percentile(), confidence);
    }

    private static void add(MinecraftServer server, ItemStack stack, Set<ValueKey.ItemValue> visited, Map<ValueKey.ItemValue, Double> samples) {
        if (stack.isEmpty()) return;
        ValueKey.ItemValue key = ValueKey.ItemValue.of(stack);
        if (!visited.add(key)) return;
        OptionalDouble value = ItemValues.get(server, stack);
        if (value.isPresent() && Double.isFinite(value.getAsDouble()) && value.getAsDouble() > 0)
            samples.put(key, value.getAsDouble());
    }

    /**
     * Lower empirical quantile: never interpolate a lone extreme into a small sample's typical high value.
     */
    private static double percentile(List<Double> samples, double percentile) {
        if (samples.isEmpty()) return 0;
        samples.sort(Double::compare);
        return samples.get((int) Math.floor((samples.size() - 1) * percentile));
    }
}
