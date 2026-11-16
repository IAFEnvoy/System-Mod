package com.iafenvoy.system.module.client;

import com.iafenvoy.system.data.input.SpaceTakeInput;
import com.iafenvoy.system.data.output.SpaceOutput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * Client names are selectors only; the server rechecks ownership, contents and available quantities.
 */
public final class SpaceSelectionCache {
    private long revision = -1;
    private List<SpaceOutput.Entry> entries = List.of();
    private Language language;
    private Map<String, List<String>> itemNames = Map.of();

    public void receive(SpaceOutput output) {
        if (output.revision() <= this.revision) return;
        this.revision = output.revision();
        this.entries = output.entries();
    }

    public SpaceTakeInput resolve(String text) {
        SpaceTakeInput input = SpaceTakeInput.parse(text);
        if (input == null) return null;
        String query = normalize(input.query());
        List<SpaceOutput.Entry> matches = this.entries.stream().filter(entry -> !entry.blocked())
                .filter(entry -> normalize(entry.name().getString()).equals(query) || normalize(entry.searchId()).equals(query)).toList();
        if (matches.size() == 1)
            return new SpaceTakeInput(matches.getFirst().id(), "", input.amount(), input.stack(), false);
        if (matches.size() > 1) {
            // Component variants share an item ID; send that ID to obtain the server's ambiguity card.
            List<String> ids = matches.stream().map(SpaceOutput.Entry::searchId).distinct().toList();
            return ids.size() == 1 ? new SpaceTakeInput("", ids.getFirst(), input.amount(), input.stack(), false) : input;
        }
        this.indexNames();
        List<String> ids = this.itemNames.getOrDefault(query, List.of());
        return ids.size() == 1 ? new SpaceTakeInput("", ids.getFirst(), input.amount(), input.stack(), false) : input;
    }

    private void indexNames() {
        Language current = Language.getInstance();
        if (this.language == current) return;
        Map<String, List<String>> names = new HashMap<>();
        BuiltInRegistries.ITEM.forEach(item -> {
            try {
                String name = normalize(new ItemStack(item).getHoverName().getString());
                if (!name.isBlank())
                    names.computeIfAbsent(name, _ -> new ArrayList<>()).add(BuiltInRegistries.ITEM.getKey(item).toString());
            } catch (RuntimeException ignored) {
                // Broken modded item names cannot prevent withdrawal of other contents.
            }
        });
        names.replaceAll((_, ids) -> List.copyOf(ids));
        this.itemNames = Map.copyOf(names);
        this.language = current;
    }

    private static String normalize(String text) {
        return text.strip().toLowerCase(Locale.ROOT);
    }

    public void clear() {
        this.revision = -1;
        this.entries = List.of();
        this.language = null;
        this.itemNames = Map.of();
    }
}
