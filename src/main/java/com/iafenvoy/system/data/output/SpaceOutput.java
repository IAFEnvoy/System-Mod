package com.iafenvoy.system.data.output;

import com.iafenvoy.saturn.data.IconReference;
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

import java.util.List;
import java.util.Map;

/**
 * One provider-neutral snapshot; no native content or authoritative balances return from the client.
 */
public record SpaceOutput(long revision, List<Entry> entries, Component feedback) implements CardData {
    public record Entry(String id, String searchId, Component name, IconReference icon, long amount, boolean blocked) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Entry::id),
                Codec.STRING.fieldOf("searchId").forGetter(Entry::searchId),
                ComponentSerialization.CODEC.fieldOf("name").forGetter(Entry::name),
                IconReference.CODEC.fieldOf("icon").forGetter(Entry::icon),
                Codec.LONG.fieldOf("amount").forGetter(Entry::amount),
                Codec.BOOL.fieldOf("blocked").forGetter(Entry::blocked)
        ).apply(i, Entry::new));
    }

    public static final MapCodec<SpaceOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.LONG.fieldOf("revision").forGetter(SpaceOutput::revision),
            Entry.CODEC.listOf().fieldOf("entries").forGetter(SpaceOutput::entries),
            ComponentSerialization.CODEC.optionalFieldOf("feedback", Component.empty()).forGetter(SpaceOutput::feedback)
    ).apply(i, SpaceOutput::new));

    public SpaceOutput {
        entries = List.copyOf(entries);
    }

    @Override
    public MapCodec<SpaceOutput> codec() {
        return CODEC;
    }

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "space"))
                .title("system.ui.nav_inbox").summary("system.result.inbox").put("feedback", this.feedback)
                .enable("feedback", !this.feedback.equals(Component.empty()));
        builder.list("entries", this.entries, (prefix, entry) -> builder.put(prefix + "name", entry.name)
                .put(prefix + "icon", entry.icon).put(prefix + "amount", Long.toString(entry.amount)).put(prefix + "blocked", entry.blocked));
        Identifier take = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "space_take");
        for (int index = 0; index < this.entries.size(); index++) {
            Entry entry = this.entries.get(index);
            builder.serverButton("take." + index, "system.action.space_take", take, Map.of("entry", entry.id),
                            CardButton.Placement.BODY, CardButton.Behavior.REPLACE)
                    .actionParameter("shift_value", CardEventValue.encode(take, Map.of("entry", entry.id, "stack", "true")));
            builder.actionParameter("disabled", entry.blocked);
        }
        return builder;
    }
}
