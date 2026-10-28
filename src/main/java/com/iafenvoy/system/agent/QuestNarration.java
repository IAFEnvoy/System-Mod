package com.iafenvoy.system.agent;

import com.iafenvoy.system.agent.prompt.SystemPrompts;
import com.iafenvoy.system.agent.store.AgentStore;
import com.iafenvoy.system.card.SpeechContent;
import com.iafenvoy.system.config.file.ModelConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Read-only narration runs separately from chat; only a trusted source supplies model input.
 */
public final class QuestNarration implements AutoCloseable {
    private final AgentEngine engine;
    private final QuestBackgrounds backgrounds = new QuestBackgrounds();
    private final TaskExecutor workers = new TaskExecutor("agent-quest-speech", 2, 32);
    private final Map<UUID, ExecutionContext> active = new ConcurrentHashMap<>();

    private record SnapshotKey(String id, String language, String promptRevision, String endpoint, String model,
                               Quest quest) {
    }

    private record Prepared(SpeechContent content, long expires) {
    }

    private final Map<SnapshotKey, Prepared> prepared = new ConcurrentHashMap<>();
    private final Set<SnapshotKey> preparing = ConcurrentHashMap.newKeySet();
    private final Map<Key, Cached> cache = new LinkedHashMap<>(16, 0.75F, true);
    private final Map<Key, CompletableFuture<Map<String, String>>> loading = new ConcurrentHashMap<>();
    private volatile Source source;

    QuestNarration(AgentEngine engine) {
        this.engine = engine;
    }

    @FunctionalInterface
    public interface Source {
        CompletionStage<Quest> load(ExecutionContext context, String questId);
    }

    public void source(Source source) {
        this.source = Objects.requireNonNull(source);
    }

    public void cacheFile(Path file) {
        this.backgrounds.file(file);
    }

    public record Line(Component text, Optional<String> unformatted) {
        public static Line formatted(String key, Object... arguments) {
            return new Line(Component.translatable(key, arguments), Optional.empty());
        }

        public static Line unknown(Component fallback, String source) {
            return new Line(fallback, Optional.of(source));
        }
    }

    public record Quest(Component title, List<Line> requirements, List<Line> rewards, String description) {
        public Quest {
            requirements = List.copyOf(requirements);
            rewards = List.copyOf(rewards);
        }
    }

    private record Input(Map<String, String> entries) {
        static final MapCodec<Input> CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf("entries")
                .xmap(Input::new, Input::entries);

        Input {
            entries = Map.copyOf(entries);
        }
    }

    private record Key(String language, String instruction, String endpoint, String model, Input input) {
    }

    private record Cached(Map<String, String> entries, long retryAfter) {
    }

    /**
     * Returns immediately; unfamiliar requirements and narrative classification warm the server cache.
     */
    public SpeechContent content(UUID player, String id, Quest quest, Component main) {
        ModelConfig model = ModelConfig.INSTANCE.get();
        SnapshotKey key = new SnapshotKey(id, this.engine.language(player), this.engine.prompts().revision(),
                model.baseUrl(), model.model(), quest);
        Prepared cached = this.prepared.get(key);
        if (cached != null && cached.expires() > System.currentTimeMillis())
            return new SpeechContent(main, cached.content().requirements(), cached.content().rewards(), cached.content().descriptions());
        if (this.source != null && this.preparing.add(key)) this.warm(player, key);
        return this.content(main, quest, Map.of(), "");
    }

    private SpeechContent content(Component main, Quest quest, Map<String, String> summaries, String background) {
        List<Component> requirements = new ArrayList<>();
        List<Component> rewards = new ArrayList<>();
        append(requirements, summaries, "requirement.", "system.tts.quest.requirements", quest.requirements());
        append(rewards, summaries, "reward.", "system.tts.quest.rewards", quest.rewards());
        return new SpeechContent(main, requirements, rewards,
                background.isBlank() ? List.of() : List.of(Component.literal(background)));
    }

    private void warm(UUID player, SnapshotKey key) {
        Source source = this.source;
        long incarnation = this.engine.incarnation(player);
        this.workers.submit(() -> {
            AgentStore store = this.engine.playerStore(player, incarnation);
            long epoch = store.read(player, data -> {
                if (!data.bound()) throw new AgentException("NOT_BOUND");
                return data.conversation.memoryEpoch;
            });
            ExecutionContext context = new ExecutionContext(this.engine, player, UUID.randomUUID().toString(), null,
                    System.currentTimeMillis(), epoch, new ExecutionPolicy(4, 0, 8, 0, 32768, 1024, 131072, 14500, 4000), store, incarnation);
            if (this.active.putIfAbsent(player, context) != null) return null;
            try {
                Quest current = source.load(context, key.id()).toCompletableFuture().get(context.remainingMillis(), TimeUnit.MILLISECONDS);
                if (!current.equals(key.quest())) return null;
                Map<String, String> unknown = new LinkedHashMap<>();
                collect(unknown, "requirement.", current.requirements());
                collect(unknown, "reward.", current.rewards());
                Map<String, String> summaries = unknown.isEmpty() ? Map.of() : this.summarize(context, new Input(unknown));
                String background = this.backgrounds.resolve(context, key.id(), current.title().getString(), current.description());
                context.ensureMemoryCurrent();
                Quest checked = source.load(context, key.id()).toCompletableFuture().get(context.remainingMillis(), TimeUnit.MILLISECONDS);
                if (checked.equals(current)) {
                    if (this.prepared.size() >= 512) this.prepared.clear();
                    // Fallbacks are not cached here: model failures may retry after their existing cooldown.
                    if (unknown.isEmpty() || summaries.size() == unknown.size())
                        this.prepared.put(key, new Prepared(this.content(Component.empty(), checked, summaries, background), System.currentTimeMillis() + 30000));
                }
            } finally {
                this.active.remove(player, context);
            }
            return null;
        }).whenComplete((_, _) -> this.preparing.remove(key));
    }

    private static void collect(Map<String, String> entries, String prefix, List<Line> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String key = prefix + i;
            lines.get(i).unformatted().ifPresent(value -> entries.put(key, value));
        }
    }

    private Map<String, String> summarize(ExecutionContext context, Input input) throws Exception {
        String instruction = context.prompt(SystemPrompts.QUEST_NARRATION);
        ModelConfig model = ModelConfig.INSTANCE.get();
        Map<String, String> prepared = new HashMap<>();
        Map<String, String> missing = new LinkedHashMap<>();
        for (String source : new TreeSet<>(input.entries().values())) {
            Key key = new Key(context.language(), instruction, model.baseUrl(), model.model(), new Input(Map.of("0", source)));
            Cached cached;
            synchronized (this.cache) {
                cached = this.cache.get(key);
            }
            if (cached != null && (cached.retryAfter() == 0 || cached.retryAfter() > System.currentTimeMillis())) {
                if (cached.entries().containsKey("0")) prepared.put(source, cached.entries().get("0"));
            } else missing.put(Integer.toString(missing.size()), source);
        }
        if (!missing.isEmpty() && context.store().read(context.player(), data -> data.externalModel)) {
            Input batch = new Input(missing);
            Map<String, String> summaries = this.summarizeBatch(context,
                    new Key(context.language(), instruction, model.baseUrl(), model.model(), batch));
            summaries.forEach((id, text) -> prepared.put(missing.get(id), text));
        }
        Map<String, String> result = new HashMap<>();
        input.entries().forEach((id, source) -> {
            String summary = prepared.get(source);
            if (summary != null) result.put(id, summary);
        });
        return Map.copyOf(result);
    }

    private Map<String, String> summarizeBatch(ExecutionContext context, Key key) throws Exception {
        CompletableFuture<Map<String, String>> future = new CompletableFuture<>();
        CompletableFuture<Map<String, String>> previous = this.loading.putIfAbsent(key, future);
        if (previous != null) return previous.get(context.remainingMillis(), TimeUnit.MILLISECONDS);
        try {
            Map<String, String> entries;
            boolean success;
            try {
                Input result = context.model(key.instruction(), Input.CODEC, key.input(), Input.CODEC.codec());
                success = result.entries().keySet().equals(key.input().entries().keySet())
                        && result.entries().values().stream().noneMatch(String::isBlank);
                entries = success ? result.entries() : Map.of();
            } catch (Exception error) {
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                success = false;
                entries = Map.of();
            }
            synchronized (this.cache) {
                // Content, language, prompt and model changes miss the cache; failures only suppress immediate retries.
                for (Map.Entry<String, String> source : key.input().entries().entrySet()) {
                    Key fragment = new Key(key.language(), key.instruction(), key.endpoint(), key.model(), new Input(Map.of("0", source.getValue())));
                    this.cache.put(fragment, new Cached(success ? Map.of("0", entries.get(source.getKey())) : Map.of(),
                            success ? 0 : System.currentTimeMillis() + 30000));
                }
                while (this.cache.size() > 512) this.cache.remove(this.cache.keySet().iterator().next());
            }
            future.complete(entries);
            return entries;
        } finally {
            future.complete(Map.of());
            this.loading.remove(key, future);
        }
    }

    private static void append(List<Component> output, Map<String, String> summaries, String prefix, String label, List<Line> lines) {
        if (lines.isEmpty()) return;
        MutableComponent body = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) body.append(Component.translatable("system.tts.quest.separator"));
            String summary = summaries.get(prefix + i);
            body.append(summary == null ? lines.get(i).text() : Component.literal(summary));
        }
        output.add(Component.translatable(label, body));
    }

    public void cancel(UUID player) {
        ExecutionContext context = this.active.get(player);
        if (context != null) context.cancel();
    }

    @Override
    public void close() {
        this.active.values().forEach(ExecutionContext::cancel);
        this.workers.close();
        synchronized (this.cache) {
            this.cache.clear();
        }
        this.prepared.clear();
        this.preparing.clear();
    }
}
