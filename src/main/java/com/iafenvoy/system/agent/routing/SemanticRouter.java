package com.iafenvoy.system.agent.routing;

import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.TypedPayload;
import com.iafenvoy.system.config.file.SemanticConfig;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;

/**
 * A lightweight optional accelerator; uncertain requests keep the single-call LLM fallback.
 */
public final class SemanticRouter implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern COMPLEX = Pattern.compile(
            "不|别|没|取消|如果|假如|是否|能否|怎么|如何|为什么|顺便|然后|并且|同时|那个|这个|上一个|第二个|全部|所有奖励|多少钱|"
                    + "[0-9]+\\s*(?:个|份|组)|[;；\\r\\n]|\\b(?:not|no|never|don't|do not|cannot|can't|isn't|without|except|instead|but|if|unless|while|then|also|and|or|that|this|it|them|those|these|previous|second|all rewards|why)\\b",
            Pattern.CASE_INSENSITIVE);

    record Attempt(RouteDecision decision, List<ModelRoute.Candidate> hints, String reason) {
        static Attempt fallback(String reason) {
            return new Attempt(null, List.of(), reason);
        }
    }

    private final RouteCache cache = new RouteCache();
    private final Map<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final AtomicBoolean started = new AtomicBoolean(), closed = new AtomicBoolean();
    private volatile String state = "NOT_STARTED";
    private volatile long lastMicros;
    private volatile RoutingDiagnostics.Match lastMatch;
    private final AtomicLong totalMicros = new AtomicLong(), maxMicros = new AtomicLong();
    private volatile SemanticIndex index;
    private Map<String, RoutingProfile.Bound> profiles = Map.of();

    public synchronized void start(Collection<RoutingProfile.Bound> registrations) {
        if (!this.started.compareAndSet(false, true)) throw new IllegalStateException("Routing already started");
        if (this.closed.get()) return;
        if (SemanticConfig.INSTANCE.get().mode() == SemanticConfig.Mode.OFF) {
            this.state = "OFF";
            return;
        }
        this.state = "LOADING";
        try {
            Map<String, RoutingProfile.Bound> registered = new LinkedHashMap<>();
            registrations.forEach(profile -> registered.put(profile.moduleId().toString(), profile));
            this.profiles = Map.copyOf(registered);
            this.index = new SemanticIndex(this.profiles.values());
            this.state = "READY";
            LOGGER.info("Lexical routing ready: {} examples, mode {}", this.index.size(), SemanticConfig.INSTANCE.get().mode());
        } catch (Exception error) {
            this.state = "UNAVAILABLE";
            this.index = null;
            LOGGER.warn("Lexical routing unavailable; retaining LLM fallback", error);
        }
    }

    Attempt match(ExecutionContext context, String raw) throws Exception {
        long start = System.nanoTime();
        try {
            Attempt attempt = this.attempt(context, raw);
            this.counters.computeIfAbsent(attempt.reason(), ignored -> new LongAdder()).increment();
            return attempt;
        } finally {
            long micros = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - start);
            this.lastMicros = micros;
            this.totalMicros.addAndGet(micros);
            this.maxMicros.accumulateAndGet(micros, Math::max);
        }
    }

    private Attempt attempt(ExecutionContext context, String raw) throws Exception {
        context.ensureMemoryCurrent();
        if (SemanticConfig.INSTANCE.get().mode() == SemanticConfig.Mode.OFF) return Attempt.fallback("OFF");
        SemanticIndex current = this.index;
        if (current == null || this.closed.get()) return Attempt.fallback("NOT_READY");
        String language = context.language().split("_", 2)[0], text = LexicalEncoder.normalize(raw);
        String rejected = this.guard(text, language);
        if (rejected != null) return Attempt.fallback(rejected);
        RouteCache.Key key = this.key(context, language, text);
        String cached = key == null ? null : this.cache.get(key);
        if (cached != null && SemanticConfig.INSTANCE.get().mode() == SemanticConfig.Mode.ENFORCE) {
            RoutingProfile.Bound profile = this.profiles.get(cached);
            TypedPayload<?> input = profile == null || !profile.direct() ? null : profile.arguments().parse(text);
            if (input != null)
                return new Attempt(new RouteDecision(input, RouteSource.ROUTE_CACHE), List.of(), "CACHE_HIT");
        }
        SemanticIndex.Ranking ranking = current.search(text, language);
        context.ensureMemoryCurrent();
        SemanticIndex.Match best = ranking.first();
        if (best == null) return Attempt.fallback("NO_CANDIDATE");
        List<ModelRoute.Candidate> hints = ranking.matches().stream().limit(3)
                .map(match -> new ModelRoute.Candidate(match.profile().moduleId().toString(), match.score())).toList();
        SemanticConfig.Threshold threshold = SemanticConfig.INSTANCE.get().threshold(best.profile().moduleId().toString(), language);
        String reason;
        TypedPayload<?> input = null;
        // Similarity is not a calibrated probability; direct routing requires every safety/coverage gate.
        if (ranking.writeRequest()) reason = "WRITE_REQUEST";
        else if (!ranking.hasTopic()) reason = "NO_TOPIC";
        else if (!best.profile().direct()) reason = "NO_DIRECT_PERMISSION";
        else if (!best.topicsCovered()) reason = "UNMATCHED_TOPIC";
        else if (best.coverage() + 1e-9 < SemanticConfig.INSTANCE.get().minCoverage()) reason = "LOW_COVERAGE";
        else if (best.score() + 1e-9 < threshold.score()) reason = "LOW_SCORE";
        else if (ranking.margin() + 1e-9 < threshold.margin()) reason = "SMALL_MARGIN";
        else {
            input = best.profile().arguments().parse(text);
            reason = input == null ? "MISSING_ARGUMENTS" : "DIRECT";
        }
        this.lastMatch = new RoutingDiagnostics.Match(best.profile().moduleId().toString(), language, best.score(), ranking.margin(), best.coverage(), reason);
        if (SemanticConfig.INSTANCE.get().mode() == SemanticConfig.Mode.SHADOW) {
            // Shadow mode records local decisions without changing routing or supplying candidate hints.
            this.counters.computeIfAbsent("SHADOW_" + reason, ignored -> new LongAdder()).increment();
            return Attempt.fallback("SHADOW");
        }
        return new Attempt(input == null ? null : new RouteDecision(input, RouteSource.SEMANTIC), hints, reason);
    }

    /**
     * Remember only a validated successful, independently parsed read intent, never model parameters.
     */
    void remember(ExecutionContext context, RouteDecision decision, String raw) throws Exception {
        if (SemanticConfig.INSTANCE.get().mode() != SemanticConfig.Mode.ENFORCE || this.closed.get() || this.index == null || decision.source() != RouteSource.LLM)
            return;
        RoutingProfile.Bound profile = this.profiles.get(decision.moduleId());
        if (profile == null || !profile.direct()) return;
        String language = context.language().split("_", 2)[0], text = LexicalEncoder.normalize(raw);
        if (this.guard(text, language) != null) return;
        TypedPayload<?> parsed = profile.arguments().parse(text);
        if (parsed == null || !parsed.equals(decision.input())) return;
        // Unknown or unmatched topic content must not be turned into an exact intent shortcut.
        SemanticIndex.Ranking ranking = this.index.search(text, language);
        if (ranking.writeRequest() || !ranking.hasTopic() || ranking.matches().stream().noneMatch(match -> match.profile().moduleId().toString().equals(decision.moduleId())
                && match.topicsCovered() && match.coverage() + 1e-9 >= SemanticConfig.INSTANCE.get().minCoverage()))
            return;
        RouteCache.Key key = this.key(context, language, text);
        if (key != null) context.store().read(context.player(), data -> {
            context.ensureMemoryCurrent();
            if (data.saveHistory) this.cache.put(key, decision.moduleId());
            return null;
        });
    }

    private RouteCache.Key key(ExecutionContext context, String language, String text) throws Exception {
        RouteCache.Scope scope = context.store().read(context.player(), data -> {
            context.ensureMemoryCurrent();
            return data.saveHistory ? this.cache.scope(context.player(), context.incarnation(), context.memoryEpoch()) : null;
        });
        // A reloaded prompt must not reuse or be repopulated by decisions from the previous revision.
        return scope == null ? null : this.cache.key(scope, language, context.promptRevision() + "\n" + text);
    }

    private String guard(String text, String language) {
        if (text.isBlank() || text.codePointCount(0, text.length()) > SemanticConfig.INSTANCE.get().maxCharacters())
            return "TEXT_LIMIT";
        if (!SemanticConfig.INSTANCE.get().languages().contains(language) || !supportedText(text, language))
            return "LANGUAGE";
        return COMPLEX.matcher(text).find() ? "COMPLEX_REQUEST" : null;
    }

    private static boolean supportedText(String text, String language) {
        boolean han = false;
        for (int point : text.codePoints().toArray()) {
            Character.UnicodeScript script = Character.UnicodeScript.of(point);
            if (script == Character.UnicodeScript.HAN) han = true;
            else if (Character.isLetter(point) && script != Character.UnicodeScript.LATIN) return false;
        }
        return !language.equals("en") || !han;
    }

    public void clear(UUID player) {
        this.cache.clear(player);
    }

    public RoutingDiagnostics diagnostics() {
        Map<String, Long> counts = new LinkedHashMap<>();
        this.counters.forEach((key, count) -> counts.put(key, count.sum()));
        return new RoutingDiagnostics(SemanticConfig.ALGORITHM, SemanticConfig.INSTANCE.get().mode().name(), this.state,
                this.index == null ? 0 : this.index.size(), this.cache.size(), Map.copyOf(counts),
                Optional.ofNullable(this.lastMatch), this.lastMicros, this.totalMicros.get(), this.maxMicros.get());
    }

    @Override
    public synchronized void close() {
        if (!this.closed.compareAndSet(false, true)) return;
        this.index = null;
        this.profiles = Map.of();
        this.state = "CLOSED";
        this.cache.close();
    }
}
