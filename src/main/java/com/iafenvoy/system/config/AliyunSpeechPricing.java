package com.iafenvoy.system.config;

import com.iafenvoy.system.speech.tts.TtsProvider;
import com.iafenvoy.system.util.AgentCodecs;
import com.iafenvoy.system.util.CodecIO;
import com.iafenvoy.system.util.ConfigCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/** Server-configured CNY estimates; unavailable pricing leaves usage recorded but unpriced. */
public final class AliyunSpeechPricing {
    public static final String FILE_NAME = "system-speech-pricing.json";
    private static final int MAX_BYTES = 131072;
    private static final System.Logger LOGGER = System.getLogger(AliyunSpeechPricing.class.getName());
    private static volatile Map<Key, Rate> rates = Map.of();

    private AliyunSpeechPricing() {
    }

    public static synchronized void load(Path path) throws IOException {
        // Never retain a previous server's table if the current configuration cannot be loaded.
        rates = Map.of();
        Files.createDirectories(path.toAbsolutePath().getParent());
        if (Files.notExists(path)) {
            try (InputStream defaults = AliyunSpeechPricing.class.getResourceAsStream("/defaults/" + FILE_NAME)) {
                if (defaults == null) throw new IOException("Missing bundled speech pricing defaults");
                try {
                    Files.copy(defaults, path);
                } catch (FileAlreadyExistsException ignored) {
                    // Another initializer created it; load that file without replacing user configuration.
                }
            }
        }
        byte[] bytes;
        try (InputStream input = Files.newInputStream(path)) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length > MAX_BYTES) throw new IOException("Speech pricing configuration exceeds byte limit");
        try {
            Table table = CodecIO.parse(Table.CODEC, new String(bytes, StandardCharsets.UTF_8), MAX_BYTES);
            if (table.version() != 1 || !table.currency().equals("CNY") || table.rates().size() > 512)
                throw new IllegalArgumentException("Invalid speech pricing header");
            LocalDate.parse(table.asOf());
            Map<Key, Rate> next = new HashMap<>();
            for (Entry entry : table.rates()) {
                // Missing or malformed price components stay unpriced; never invent a zero-cost rate.
                if (!entry.valid()) {
                    LOGGER.log(System.Logger.Level.WARNING, "Skipping invalid speech pricing entry");
                    continue;
                }
                Key key = new Key(entry.provider(), entry.model(), entry.region());
                Rate rate = new Rate(entry.input().orElseThrow(), entry.output().orElseThrow(), entry.unit().orElseThrow(), entry.units());
                if (next.putIfAbsent(key, rate) != null)
                    LOGGER.log(System.Logger.Level.WARNING, "Skipping duplicate speech pricing entry");
            }
            // Publish usable entries together; an empty table explicitly disables estimates.
            rates = Map.copyOf(next);
        } catch (RuntimeException error) {
            throw new IOException("Invalid speech pricing configuration: " + path, error);
        }
    }

    public static OptionalLong estimate(String provider, String model, String region, TtsProvider.Usage usage) {
        Rate rate = rates.get(new Key(provider, model, region));
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

    /** NLS reports no billing units; a completed call is estimated from the configured per-call price. */
    public static OptionalLong estimateRecognition(String provider, String region) {
        Rate rate = rates.get(new Key(provider, "sentence-recognition", region));
        if (rate == null || rate.unit() != Unit.CALLS) return OptionalLong.empty();
        try {
            return OptionalLong.of(rate.input().movePointRight(9).divide(BigDecimal.valueOf(rate.units()), 0, RoundingMode.HALF_UP).longValueExact());
        } catch (ArithmeticException error) {
            return OptionalLong.empty();
        }
    }

    private static final Codec<BigDecimal> PRICE = AgentCodecs.DECIMAL.validate(value ->
            value.signum() < 0 || value.precision() > 18 || value.scale() < -9 || value.scale() > 9
                    ? DataResult.error(() -> "Invalid speech price") : DataResult.success(value));

    private record Entry(String provider, String model, String region, Optional<Unit> unit, int units,
                         Optional<BigDecimal> input, Optional<BigDecimal> output) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.lenientOptionalFieldOf("provider", "").forGetter(Entry::provider),
                Codec.STRING.lenientOptionalFieldOf("model", "").forGetter(Entry::model),
                Codec.STRING.lenientOptionalFieldOf("region", "").forGetter(Entry::region),
                ConfigCodecs.recovering(Codec.STRING.xmap(Unit::valueOf, Unit::name)).lenientOptionalFieldOf("unit").forGetter(Entry::unit),
                AgentCodecs.intRange(1, 1_000_000_000).lenientOptionalFieldOf("units", 0).forGetter(Entry::units),
                PRICE.lenientOptionalFieldOf("input").forGetter(Entry::input),
                PRICE.lenientOptionalFieldOf("output").forGetter(Entry::output)
        ).apply(i, Entry::new));

        boolean valid() {
            return this.provider.matches("[a-z0-9_.-]{1,64}") && !this.model.isBlank() && this.model.length() <= 128
                    && this.region.matches("[a-z0-9_.-]{1,64}") && this.unit.isPresent() && this.units > 0
                    && this.input.isPresent() && this.output.isPresent()
                    && (this.unit.get() == Unit.TOKENS || this.output.get().signum() == 0);
        }
    }

    private record Table(int version, String currency, String asOf, List<Entry> rates) {
        static final Codec<Table> CODEC = RecordCodecBuilder.create(i -> i.group(
                AgentCodecs.intRange(1, 1).lenientOptionalFieldOf("version", 1).forGetter(Table::version),
                ConfigCodecs.checked(Codec.STRING, "CNY"::equals).lenientOptionalFieldOf("currency", "CNY").forGetter(Table::currency),
                ConfigCodecs.validated(Codec.STRING, LocalDate::parse).lenientOptionalFieldOf("asOf", LocalDate.EPOCH.toString()).forGetter(Table::asOf),
                Entry.CODEC.listOf(0, 512).lenientOptionalFieldOf("rates", List.of()).forGetter(Table::rates)
        ).apply(i, Table::new));
    }

    private record Key(String provider, String model, String region) {
    }

    private enum Unit { CHARACTERS, TOKENS, CALLS }

    private record Rate(BigDecimal input, BigDecimal output, Unit unit, int units) {
    }
}
