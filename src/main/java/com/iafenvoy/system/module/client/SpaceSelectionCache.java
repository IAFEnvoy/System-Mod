package com.iafenvoy.system.module.client;

import com.iafenvoy.system.data.input.SpaceTakeInput;
import com.iafenvoy.system.data.output.SpaceOutput;
import com.iafenvoy.system.data.output.RewardChoiceOutput;
import com.iafenvoy.system.data.output.SpaceInventoryOutput;
import com.iafenvoy.system.data.space.ItemSpaceContent;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Client names are selectors only; the server rechecks ownership, contents and available quantities.
 */
public final class SpaceSelectionCache {
    private long revision = -1;
    private long itemRevision = -1;
    private List<SpaceOutput.Entry> entries = List.of();
    private List<ItemStackTemplate> items = List.of();

    public void receive(SpaceOutput output) {
        if (output.revision() <= this.revision) return;
        boolean first = this.revision < 0;
        this.revision = output.revision();
        this.entries = output.entries();
        List<ItemStackTemplate> items = this.entries.stream().filter(entry -> !entry.blocked()).flatMap(entry -> entry.item().stream())
                .map(item -> item.withCount(1)).distinct().toList();
        if (first || this.itemRevision < 0 || !this.items.equals(items)) {
            this.items = items;
            this.itemRevision++;
        }
    }

    public void receive(SpaceInventoryOutput output) {
        output.deposited().ifPresent(item -> this.include(List.of(item)));
    }

    public void receive(RewardChoiceOutput output) {
        if (!output.settled()) return;
        this.include(output.options().get(output.selected()).stacks().stream()
                .filter(stack -> stack.content() instanceof ItemSpaceContent)
                .map(stack -> ((ItemSpaceContent) stack.content()).item()).toList());
    }

    private void include(List<ItemStackTemplate> additions) {
        // Mutation replies provide search hints, never authoritative entry IDs or available quantities.
        this.entries = List.of();
        LinkedHashSet<ItemStackTemplate> items = new LinkedHashSet<>(this.items);
        additions.stream().map(item -> item.withCount(1)).forEach(items::add);
        if (this.items.equals(List.copyOf(items))) return;
        this.items = List.copyOf(items);
        this.itemRevision++;
    }

    public boolean hasSnapshot() {
        return this.revision >= 0;
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
        return input;
    }

    public long itemRevision() {
        return this.itemRevision;
    }

    public List<ItemStackTemplate> items() {
        return this.items;
    }

    private static String normalize(String text) {
        return text.strip().toLowerCase(Locale.ROOT);
    }

    public void clear() {
        this.revision = -1;
        this.itemRevision = -1;
        this.entries = List.of();
        this.items = List.of();
    }
}
