package com.iafenvoy.system.data.output;

import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardButton;
import com.iafenvoy.system.data.card.CardData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A bounded display snapshot contains no executable rules or predicates.
 */
public record AchievementsOutput(List<Entry> entries, int offset, int total, int completed,
                                 List<Component> series) implements CardData {
    public static final int PAGE_SIZE = 18;
    public static final MapCodec<AchievementsOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Entry.CODEC.listOf().fieldOf("entries").forGetter(AchievementsOutput::entries),
            Codec.INT.fieldOf("offset").forGetter(AchievementsOutput::offset),
            Codec.INT.fieldOf("total").forGetter(AchievementsOutput::total),
            Codec.INT.fieldOf("completed").forGetter(AchievementsOutput::completed),
            ComponentSerialization.CODEC.listOf().fieldOf("series").forGetter(AchievementsOutput::series)
    ).apply(i, AchievementsOutput::new));

    public AchievementsOutput {
        entries = List.copyOf(entries);
        series = List.copyOf(series);
    }

    public record Entry(Component label, Component description, Component series, Component reward, long progress,
                        long target,
                        boolean completed, boolean transferred, Optional<String> entitlement) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                ComponentSerialization.CODEC.fieldOf("label").forGetter(Entry::label),
                ComponentSerialization.CODEC.fieldOf("description").forGetter(Entry::description),
                ComponentSerialization.CODEC.fieldOf("series").forGetter(Entry::series),
                ComponentSerialization.CODEC.fieldOf("reward").forGetter(Entry::reward),
                Codec.LONG.fieldOf("progress").forGetter(Entry::progress), Codec.LONG.fieldOf("target").forGetter(Entry::target),
                Codec.BOOL.fieldOf("completed").forGetter(Entry::completed), Codec.BOOL.fieldOf("transferred").forGetter(Entry::transferred),
                Codec.STRING.optionalFieldOf("entitlement").forGetter(Entry::entitlement)
        ).apply(i, Entry::new));
    }

    @Override
    public MapCodec<AchievementsOutput> codec() {
        return CODEC;
    }

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(id("achievements")).title("system.ui.nav_achievements").summary("system.ui.nav_achievements")
                .put("overview", Component.translatable("system.achievements.overview", this.completed, this.total));
        builder.list("series", this.series, (prefix, text) -> builder.put(prefix + "text", text));
        builder.list("entries", this.entries, (prefix, entry) -> builder.put(prefix + "label", entry.label())
                .put(prefix + "description", entry.description()).put(prefix + "series", entry.series())
                .put(prefix + "reward", Component.translatable("system.achievements.reward", entry.reward()))
                .put(prefix + "progress_text", Component.translatable("system.achievements.progress", entry.progress(), entry.target()))
                .put(prefix + "progress", 100.0 * entry.progress() / entry.target())
                .put(prefix + "status", Component.translatable(entry.transferred() ? "system.records.transferred" : entry.completed() ? "system.records.available" : "system.achievements.incomplete")));
        for (int index = 0; index < this.entries.size(); index++) {
            Entry entry = this.entries.get(index);
            builder.serverButton("claim." + index, "system.records.claim", id("record_claim"), Map.of("entitlement", entry.entitlement().orElse("")),
                            CardButton.Placement.BODY, CardButton.Behavior.REPLACE)
                    .actionParameter("disabled", !entry.completed() || entry.transferred());
        }
        if (this.offset > 0)
            builder.serverButton("previous", "system.action.previous", id("achievement_query"), Map.of("offset", Integer.toString(Math.max(0, this.offset - PAGE_SIZE)))).actionParameter("footer", true);
        if (this.offset + this.entries.size() < this.total)
            builder.serverButton("next", "system.action.more_items", id("achievement_query"), Map.of("offset", Integer.toString(this.offset + PAGE_SIZE))).actionParameter("footer", true);
        return builder.serverButton("refresh", "system.action.job_refresh", id("achievement_query"), Map.of()).actionParameter("footer", true)
                .serverButton("records", "system.records.title", id("record_query"), Map.of()).actionParameter("footer", true);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("system", path);
    }
}
