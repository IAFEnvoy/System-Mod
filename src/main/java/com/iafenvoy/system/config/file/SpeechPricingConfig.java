package com.iafenvoy.system.config.file;

import com.iafenvoy.system.config.ConfigInstance;
import com.iafenvoy.system.speech.tts.TtsProvider;
import com.iafenvoy.system.util.codec.CodecIO;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import org.slf4j.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Server-configured CNY estimates; unavailable pricing leaves usage recorded but unpriced.
 */
public final class SpeechPricingConfig {
    public static final String FILE_NAME = "system-speech-pricing.json";
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Table table;
    private final Map<Key, Rate> rates;

    private SpeechPricingConfig(Table table) {
        this.table = table;
        Map<Key, Rate> next = new HashMap<>();
        for (Entry entry : table.rates()) {
            // Missing or malformed price components stay unpriced; never invent a zero-cost rate.
            if (!entry.valid()) {
                LOGGER.warn("Skipping invalid speech pricing entry");
                continue;
            }
            Key key = new Key(entry.provider(), entry.model(), entry.region());
            Rate rate = new Rate(entry.input().orElseThrow(), entry.output().orElseThrow(), entry.unit().orElseThrow(), entry.units());
            if (next.putIfAbsent(key, rate) != null)
                LOGGER.warn("Skipping duplicate speech pricing entry");
        }
        this.rates = Map.copyOf(next);
    }

    public static OptionalLong estimate(String provider, String model, String region, TtsProvider.Usage usage) {
        Rate rate = INSTANCE.get().rates.get(new Key(provider, model, region));
        if (!usage.reported() || rate == null || !rate.unit().name().equals(usage.unit().name()))
            return OptionalLong.empty();
        BigDecimal units = rate.unit() == Unit.CHARACTERS ? BigDecimal.valueOf(usage.characters())
                : BigDecimal.valueOf(usage.inputTokens());
        BigDecimal cost = units.multiply(rate.input()).add(BigDecimal.valueOf(usage.outputTokens()).multiply(rate.output()));
        // Store billionths of a yuan; round once per call, never once per character/token.
        try {
            return OptionalLong.of(cost.movePointRight(9).divide(BigDecimal.valueOf(rate.units()), 0, RoundingMode.HALF_UP).longValueExact());
        } catch (ArithmeticException error) {
            return OptionalLong.empty();
        }
    }

    /**
     * NLS reports no billing units; a completed call is estimated from the configured per-call price.
     */
    public static OptionalLong estimateRecognition(String provider, String region) {
        Rate rate = INSTANCE.get().rates.get(new Key(provider, "sentence-recognition", region));
        if (rate == null || rate.unit() != Unit.CALLS) return OptionalLong.empty();
        try {
            return OptionalLong.of(rate.input().movePointRight(9).divide(BigDecimal.valueOf(rate.units()), 0, RoundingMode.HALF_UP).longValueExact());
        } catch (ArithmeticException error) {
            return OptionalLong.empty();
        }
    }

    private static final Codec<BigDecimal> PRICE = CodecIO.checked(
            Codec.STRING.comapFlatMap(x -> CodecIO.mapOrError(() -> new BigDecimal(x)), BigDecimal::toString), value -> value.signum() >= 0);

    private record Entry(String provider, String model, String region, Optional<Unit> unit, int units,
                         Optional<BigDecimal> input, Optional<BigDecimal> output) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.lenientOptionalFieldOf("provider", "").forGetter(Entry::provider),
                Codec.STRING.lenientOptionalFieldOf("model", "").forGetter(Entry::model),
                Codec.STRING.lenientOptionalFieldOf("region", "").forGetter(Entry::region),
                Codec.STRING.comapFlatMap(value -> CodecIO.mapOrError(() -> Unit.valueOf(value)), Unit::name).lenientOptionalFieldOf("unit").forGetter(Entry::unit),
                ExtraCodecs.POSITIVE_INT.lenientOptionalFieldOf("units", 0).forGetter(Entry::units),
                PRICE.lenientOptionalFieldOf("input").forGetter(Entry::input),
                PRICE.lenientOptionalFieldOf("output").forGetter(Entry::output)
        ).apply(i, Entry::new));

        boolean valid() {
            return !this.provider.isBlank() && !this.model.isBlank() && !this.region.isBlank()
                    && this.unit.isPresent() && this.units > 0
                    && this.input.isPresent() && this.output.isPresent()
                    && (this.unit.get() == Unit.TOKENS || this.output.get().signum() == 0);
        }
    }

    private record Table(int version, String currency, String asOf, List<Entry> rates) {
        static final Codec<Table> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.lenientOptionalFieldOf("version", 1).forGetter(Table::version),
                CodecIO.checked(Codec.STRING, "CNY"::equals).lenientOptionalFieldOf("currency", "CNY").forGetter(Table::currency),
                Codec.STRING.lenientOptionalFieldOf("asOf", LocalDate.EPOCH.toString()).forGetter(Table::asOf),
                Entry.CODEC.listOf().lenientOptionalFieldOf("rates", List.of()).forGetter(Table::rates)
        ).apply(i, Table::new));
    }

    public static final Codec<SpeechPricingConfig> CODEC = Table.CODEC.xmap(SpeechPricingConfig::new, value -> value.table);
    public static final ConfigInstance<SpeechPricingConfig> INSTANCE = ConfigInstance.of(FILE_NAME, CODEC,
            new SpeechPricingConfig(new Table(1, "CNY", LocalDate.EPOCH.toString(), List.of())));

    private record Key(String provider, String model, String region) {
    }

    private enum Unit {CHARACTERS, TOKENS, CALLS}

    private record Rate(BigDecimal input, BigDecimal output, Unit unit, int units) {
    }
}
