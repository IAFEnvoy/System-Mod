package com.iafenvoy.system.agent.builtin.output;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Authorized evidence only; an empty list is distinct from a failed or cancelled database query.
 */
public record DatabaseQueryOutput(List<Source> sources) {
    public static final Codec<DatabaseQueryOutput> CODEC = Source.CODEC.listOf().fieldOf("sources")
            .xmap(DatabaseQueryOutput::new, DatabaseQueryOutput::sources).codec();

    public DatabaseQueryOutput {
        sources = List.copyOf(sources);
    }

    public record Source(String key, String title, String category, String passage) {
        public static final Codec<Source> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("key").forGetter(Source::key),
                Codec.STRING.fieldOf("title").forGetter(Source::title),
                Codec.STRING.fieldOf("category").forGetter(Source::category),
                Codec.STRING.fieldOf("passage").forGetter(Source::passage)
        ).apply(i, Source::new));
    }
}
