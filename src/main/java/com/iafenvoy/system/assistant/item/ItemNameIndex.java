package com.iafenvoy.system.assistant.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Display-name table used by local intent parsing; the integrated server shares the client language, so the
 * names match what the player typed. Must be built on the game thread, because names resolve through the language.
 */
public final class ItemNameIndex {
    private static final int MIN_NAME = 2;
    private static final int MAX_TEXT = 96;
    private final List<Entry> entries;

    private ItemNameIndex(List<Entry> entries) {
        this.entries = entries;
    }

    public static ItemNameIndex build() {
        List<Entry> entries = new ArrayList<>();
        for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
            Item item = BuiltInRegistries.ITEM.getValue(id);
            // One broken modded item must not stop the server from starting.
            try {
                String name = new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT).strip();
                // A one-character name would match almost any sentence.
                if (name.length() < MIN_NAME) continue;
                entries.add(new Entry(name, id));
            } catch (RuntimeException ignored) {
            }
        }
        // Longest name first so "diamond pickaxe" wins over "diamond"; the registry order breaks ties.
        entries.sort(Comparator.comparingInt((Entry entry) -> entry.name().length()).reversed());
        return new ItemNameIndex(List.copyOf(entries));
    }

    /**
     * Resolve one item mentioned by name inside a short request; null keeps the request with the model.
     */
    public Identifier resolve(String text) {
        if (text.isBlank() || text.length() > MAX_TEXT) return null;
        String lower = text.toLowerCase(Locale.ROOT);
        for (Entry entry : this.entries) if (lower.contains(entry.name())) return entry.id();
        return null;
    }

    private record Entry(String name, Identifier id) {
    }
}
