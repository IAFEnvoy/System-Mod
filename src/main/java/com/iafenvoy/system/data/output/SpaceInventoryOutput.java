package com.iafenvoy.system.data.output;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardButton;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.util.CardEventValue;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record SpaceInventoryOutput(List<Entry> entries, long used, int capacity, Component feedback,
                                   Optional<ItemStackTemplate> deposited) implements CardData {
    public record Entry(String reference, int slot, ItemStackTemplate item) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("reference").forGetter(Entry::reference),
                Codec.INT.fieldOf("slot").forGetter(Entry::slot),
                ItemStackTemplate.CODEC.fieldOf("item").forGetter(Entry::item)
        ).apply(i, Entry::new));
    }

    public static final MapCodec<SpaceInventoryOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Entry.CODEC.listOf(0, 36).fieldOf("entries").forGetter(SpaceInventoryOutput::entries),
            Codec.LONG.fieldOf("used").forGetter(SpaceInventoryOutput::used),
            Codec.INT.fieldOf("capacity").forGetter(SpaceInventoryOutput::capacity),
            ComponentSerialization.CODEC.fieldOf("feedback").forGetter(SpaceInventoryOutput::feedback),
            ItemStackTemplate.CODEC.optionalFieldOf("deposited").forGetter(SpaceInventoryOutput::deposited)
    ).apply(i, SpaceInventoryOutput::new));

    public SpaceInventoryOutput {
        entries = List.copyOf(entries);
    }

    public SpaceInventoryOutput(List<Entry> entries, long used, int capacity, Component feedback) {
        this(entries, used, capacity, feedback, Optional.empty());
    }

    public SpaceInventoryOutput withDeposit(ItemStackTemplate item) {
        return new SpaceInventoryOutput(this.entries, this.used, this.capacity, this.feedback, Optional.of(item.withCount(1)));
    }

    @Override
    public MapCodec<SpaceInventoryOutput> codec() {
        return CODEC;
    }

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "space_inventory"))
                .title("system.space.deposit_title").summary("system.space.deposit_hint")
                .put("capacity", Component.translatable("system.space.deposit_capacity", this.used, this.capacity))
                .put("feedback", this.feedback).enable("feedback", !this.feedback.equals(Component.empty()));
        builder.list("entries", this.entries, (prefix, entry) -> builder.put(prefix + "item", ItemPreviewOutput.of(entry.item))
                .put(prefix + "name", entry.item.create().getHoverName()).put(prefix + "amount", entry.item.count())
                .put(prefix + "slot", Component.translatable("system.space.deposit_slot", entry.slot + 1)));
        Identifier action = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "space_deposit");
        for (int index = 0; index < this.entries.size(); index++)
            builder.serverButton("deposit." + index, "system.space.deposit", action, Map.of("reference", this.entries.get(index).reference),
                            CardButton.Placement.BODY, CardButton.Behavior.REPLACE)
                    .actionParameter("shift_value", CardEventValue.encode(action, Map.of("reference", this.entries.get(index).reference, "stack", "true")))
                    .actionParameter("label", Component.translatable("system.space.deposit_entry", this.entries.get(index).item.create().getHoverName(), this.entries.get(index).item.count()))
                    .actionParameter("disabled", this.capacity <= this.used);
        return builder.serverButton("refresh", "system.action.refresh", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "space_inventory"), Map.of())
                .actionParameter("footer", true)
                .serverButton("space", "system.ui.nav_inbox", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "inbox"), Map.of()).actionParameter("footer", true);
    }
}
