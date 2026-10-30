package com.iafenvoy.system.agent;

import com.iafenvoy.system.agent.database.AgentDatabase;
import com.iafenvoy.system.agent.database.DatabaseTable;
import com.iafenvoy.system.agent.prompt.SystemPrompts;
import com.iafenvoy.system.config.file.ModelConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * World-local static background decisions, including negative results; never stores player progress.
 * Only worker threads access disk, and callers must recheck native visibility before using an entry.
 */
final class QuestBackgrounds {
    private static final DatabaseTable<Entry> TABLE = new DatabaseTable<>(ResourceLocation.fromNamespaceAndPath("system", "quest_backgrounds"), Entry.CODEC);
    private final AgentDatabase database;
    private final Map<String, CompletableFuture<String>> loading = new ConcurrentHashMap<>();
    private final Map<String, Long> retryAfter = new ConcurrentHashMap<>();

    QuestBackgrounds(AgentDatabase database) {
        this.database = database;
    }

    private record Input(String title, String description) {
        static final MapCodec<Input> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("title").forGetter(Input::title),
                Codec.STRING.fieldOf("description").forGetter(Input::description)
        ).apply(i, Input::new));
    }

    private record Background(boolean narrative, String text) {
        static final Codec<Background> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.fieldOf("narrative").forGetter(Background::narrative),
                Codec.STRING.fieldOf("text").forGetter(Background::text)
        ).apply(i, Background::new));
    }

    private record Entry(String questId, String language, String fingerprint, boolean narrative, String text) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("questId").forGetter(Entry::questId),
                Codec.STRING.fieldOf("language").forGetter(Entry::language),
                Codec.STRING.fieldOf("fingerprint").forGetter(Entry::fingerprint),
                Codec.BOOL.fieldOf("narrative").forGetter(Entry::narrative),
                Codec.STRING.fieldOf("text").forGetter(Entry::text)
        ).apply(i, Entry::new));

        String key() {
            return this.questId + ":" + this.language;
        }
    }

    String resolve(ExecutionContext context, String questId, String title, String description) throws Exception {
        String instruction = context.prompt(SystemPrompts.QUEST_BACKGROUND);
        ModelConfig model = ModelConfig.INSTANCE.get();
        String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                String.join("\0", "background-v1", title, description, instruction, model.baseUrl(), model.model()).getBytes(StandardCharsets.UTF_8)));
        String key = questId.toUpperCase(Locale.ROOT) + ":" + context.language();
        Entry cached = this.database.get(TABLE, key).orElse(null);
        if (cached != null && cached.fingerprint().equals(fingerprint))
            return cached.narrative() ? cached.text() : "";
        String flight = key + ":" + fingerprint;
        if (description.isBlank() || description.strip().equals(title.strip())
                || description.strip().matches("https?://\\S+")) {
            this.put(new Entry(questId.toUpperCase(Locale.ROOT), context.language(), fingerprint, false, ""));
            return "";
        }
        if (!context.store().read(context.player(), data -> data.externalModel)
                || this.retryAfter.getOrDefault(flight, 0L) > System.currentTimeMillis()) return "";
        CompletableFuture<String> future = new CompletableFuture<>();
        CompletableFuture<String> previous = this.loading.putIfAbsent(flight, future);
        if (previous != null) return previous.get(context.remainingMillis(), TimeUnit.MILLISECONDS);
        try {
            // A previous owner may have populated the cache between our first read and registration.
            cached = this.database.get(TABLE, key).orElse(null);
            if (cached != null && cached.fingerprint().equals(fingerprint)) {
                String text = cached.narrative() ? cached.text() : "";
                future.complete(text);
                return text;
            }
            Background result = context.model(instruction, Input.CODEC, new Input(title, description), Background.CODEC);
            if (result.narrative() && result.text().isBlank() || !result.narrative() && !result.text().isBlank())
                throw new AgentException("MODEL_INVALID");
            String text = result.narrative() ? result.text().strip() : "";
            context.ensureMemoryCurrent();
            this.put(new Entry(questId.toUpperCase(Locale.ROOT), context.language(), fingerprint, result.narrative(), text));
            this.retryAfter.remove(flight);
            future.complete(text);
            return text;
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            this.retryAfter.put(flight, System.currentTimeMillis() + 30000);
            future.complete("");
            return "";
        } finally {
            future.complete("");
            this.loading.remove(flight, future);
        }
    }

    private void put(Entry entry) {
        this.database.put(TABLE, entry.key(), entry);
    }
}
