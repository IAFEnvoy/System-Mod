package com.iafenvoy.system.agent;

import com.iafenvoy.system.agent.prompt.SystemPrompts;
import com.iafenvoy.system.agent.store.AgentStore;
import com.iafenvoy.system.config.file.ModelConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
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
    private final Set<UUID> queued = ConcurrentHashMap.newKeySet();
    private final Map<Key, Cached> cache = new LinkedHashMap<>(16, 0.75F, true);
    private final Map<Key, CompletableFuture<Map<String, String>>> loading = new ConcurrentHashMap<>();
    private volatile Source source;

    QuestNarration(AgentEngine engine) {
        this.engine = engine;
    }

    @FunctionalInterface
    public interface Source {
        CompletionStage<Quest> load(ExecutionContext context, String questId, Request request);
    }

    public void source(Source source) {
        this.source = Objects.requireNonNull(source);
    }

    public void cacheFile(Path file) {
        this.backgrounds.file(file);
    }

    public record Request(List<String> quests, boolean requirements, boolean rewards, boolean description) {
        public static final Codec<Request> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.listOf().fieldOf("quests").forGetter(Request::quests),
                Codec.BOOL.fieldOf("requirements").forGetter(Request::requirements),
                Codec.BOOL.fieldOf("rewards").forGetter(Request::rewards),
                Codec.BOOL.fieldOf("description").forGetter(Request::description)
        ).apply(i, Request::new));

        public Request {
            quests = List.copyOf(quests);
        }
    }

    public record Reply(List<Component> lines) {
        public static final Codec<Reply> CODEC = ComponentSerialization.CODEC.listOf().fieldOf("lines")
                .xmap(Reply::new, Reply::lines).codec();

        public Reply {
            lines = List.copyOf(lines);
        }

        public static Reply empty() {
            return new Reply(List.of());
        }
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

    public CompletionStage<Reply> request(UUID player, Request request) {
        // A normal notice has one quest; the startup overview includes at most three samples.
        if (request.quests().isEmpty() || request.quests().size() > 3 || this.source == null)
            return CompletableFuture.completedFuture(Reply.empty());
        if (!this.queued.add(player)) return CompletableFuture.completedFuture(Reply.empty());
        Source source = this.source;
        long incarnation = this.engine.incarnation(player);
        return this.workers.submit(() -> {
            AgentStore store = this.engine.playerStore(player, incarnation);
            long epoch = store.read(player, data -> {
                if (!data.bound) throw new AgentException("NOT_BOUND");
                return data.conversation.memoryEpoch;
            });
            ExecutionContext context = new ExecutionContext(this.engine, player, UUID.randomUUID().toString(), null,
                    System.currentTimeMillis(), epoch, new ExecutionPolicy(4, 0, 8, 0, 32768, 1024, 131072, 14500, 4000), store, incarnation);
            if (this.active.putIfAbsent(player, context) != null) throw new AgentException("BUSY");
            try {
                List<String> ids = new ArrayList<>(new LinkedHashSet<>(request.quests()));
                List<Quest> quests = new ArrayList<>();
                for (String id : ids) {
                    context.check();
                    quests.add(source.load(context, id, request).toCompletableFuture().get(context.remainingMillis(), TimeUnit.MILLISECONDS));
                }
                Map<String, String> unknown = new LinkedHashMap<>();
                for (int q = 0; q < quests.size(); q++) {
                    collect(unknown, q + ".requirement.", quests.get(q).requirements());
                    collect(unknown, q + ".reward.", quests.get(q).rewards());
                }
                Map<String, String> summaries = unknown.isEmpty() ? Map.of() : this.summarize(context, new Input(unknown));
                context.ensureMemoryCurrent();
                List<Component> lines = new ArrayList<>();
                for (int q = 0; q < quests.size(); q++) {
                    Quest quest = quests.get(q);
                    String id = ids.get(q);
                    String background = request.requirements() && request.description()
                            ? this.backgrounds.resolve(context, id, quest.title().getString(), quest.description()) : "";
                    Quest current = source.load(context, id, request).toCompletableFuture().get(context.remainingMillis(), TimeUnit.MILLISECONDS);
                    // Cached text cannot bypass a later visibility change, task edit or description concealment.
                    if (!current.equals(quest)) continue;
                    if (quests.size() > 1) lines.add(Component.translatable("system.tts.quest.name", quest.title()));
                    if (!background.isBlank()) lines.add(Component.literal(background));
                    append(lines, summaries, q + ".requirement.", "system.tts.quest.requirements", quest.requirements());
                    append(lines, summaries, q + ".reward.", "system.tts.quest.rewards", quest.rewards());
                }
                context.ensureMemoryCurrent();
                return new Reply(lines);
            } finally {
                this.active.remove(player, context);
            }
        }).whenComplete((_, _) -> this.queued.remove(player));
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
    }
}
