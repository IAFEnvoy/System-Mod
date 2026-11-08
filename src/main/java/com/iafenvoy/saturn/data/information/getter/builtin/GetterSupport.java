package com.iafenvoy.saturn.data.information.getter.builtin;

import com.iafenvoy.saturn.data.information.model.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Shared container decoding only; individual fields decide what to read and what absence means.
 */
final class GetterSupport {
    private GetterSupport() {
    }

    static <T> ViewValue<T> saved(CompoundTag data, String key, Function<CompoundTag, T> reader) {
        return data.contains(key) ? ViewValue.known(reader.apply(data), ValueOrigin.SAVED)
                : ViewValue.missing(MissingReason.NOT_CAPTURED);
    }

    static <T, R> ViewValue<R> select(ViewValue<List<T>> values, Predicate<T> matches, Function<T, Optional<R>> project) {
        if (!values.isKnown()) return ViewValue.missing(values.missingReason().orElseThrow());
        Optional<T> selected = values.value().orElseThrow().stream().filter(matches).findFirst();
        return selected.map(t -> project.apply(t).map(value -> ViewValue.known(value, values.origin()))
                .orElseGet(() -> ViewValue.missing(MissingReason.NOT_CAPTURED))).orElseGet(() -> ViewValue.missing(MissingReason.NOT_APPLICABLE));
    }

    static Map<String, PlayerViewItem> container(Player player, Container container, int size) {
        Map<String, PlayerViewItem> items = new LinkedHashMap<>();
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty())
                items.put(Integer.toString(slot), PlayerViewItem.capture(stack, player.registryAccess()));
        }
        return items;
    }

    static Map<String, PlayerViewItem> savedItems(ListTag list, SavedPlayerViewContext context) {
        Map<String, PlayerViewItem> items = new LinkedHashMap<>();
        for (Tag raw : list) {
            if (!(raw instanceof CompoundTag item)) throw new IllegalArgumentException("Invalid saved item");
            int slot = Byte.toUnsignedInt(item.getByte("Slot"));
            PlayerViewItem value = PlayerViewItem.saved(item, context.registries()).orElseThrow();
            if (items.putIfAbsent(Integer.toString(slot), value) != null)
                throw new IllegalArgumentException("Duplicate saved item slot");
        }
        return items;
    }
}
