package com.iafenvoy.system.agent.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.net.URI;
import java.util.*;

/** Public search metadata only; encrypted pages and provider reasoning never enter application observations. */
public final class DeepSeekWebSearch {
    private DeepSeekWebSearch() {
    }

    public record Page(String url, String title, String snippet) {
        public static final Codec<Page> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("url").forGetter(Page::url),
                Codec.STRING.optionalFieldOf("title", "").forGetter(Page::title),
                Codec.STRING.optionalFieldOf("snippet", "").forGetter(Page::snippet)
        ).apply(i, Page::new));

        private ModelClient.WebSource source() {
            return new ModelClient.WebSource(this.title.isBlank() ? this.url : compact(this.title, 200), this.url, compact(this.snippet, 700));
        }
    }

    public record Call(String type, String status, List<Page> sources, Optional<Page> page) {
        public static final Codec<Call> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("type", "").forGetter(Call::type),
                Codec.STRING.optionalFieldOf("status", "").forGetter(Call::status),
                Page.CODEC.listOf().lenientOptionalFieldOf("sources", List.of()).forGetter(Call::sources),
                Page.CODEC.lenientOptionalFieldOf("page").forGetter(Call::page)
        ).apply(i, Call::new));
    }

    public record Step(List<Call> calls) {
        public static final Codec<Step> CODEC = Call.CODEC.listOf().fieldOf("calls").xmap(Step::new, Step::calls).codec();
    }

    public record Location(String url, String title, int start, int end) {
        public static final Codec<Location> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("url").forGetter(Location::url),
                Codec.STRING.optionalFieldOf("title", "").forGetter(Location::title),
                Codec.INT.optionalFieldOf("start_index", -1).forGetter(Location::start),
                Codec.INT.optionalFieldOf("end_index", -1).forGetter(Location::end)
        ).apply(i, Location::new));
    }

    public record Annotation(String type, Optional<Location> citation) {
        public static final Codec<Annotation> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("type").forGetter(Annotation::type),
                Location.CODEC.optionalFieldOf("url_citation").forGetter(Annotation::citation)
        ).apply(i, Annotation::new));
    }

    public record Metadata(List<Annotation> annotations, List<Step> steps) {
        public static final MapCodec<Metadata> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Annotation.CODEC.listOf().lenientOptionalFieldOf("annotations", List.of()).forGetter(Metadata::annotations),
                Step.CODEC.listOf().lenientOptionalFieldOf("server_tool_calls", List.of()).forGetter(Metadata::steps)
        ).apply(i, Metadata::new));

        public Metadata {
            annotations = List.copyOf(annotations);
            steps = List.copyOf(steps);
        }

        public ModelClient.Reply reply(String content, int tokens) {
            Map<String, ModelClient.WebSource> observed = new LinkedHashMap<>();
            for (Step step : this.steps) for (Call call : step.calls) {
                if (!call.status.equals("completed") || !Set.of("deepseek_web_search", "deepseek_read_url").contains(call.type)) continue;
                call.sources.forEach(page -> observe(observed, page));
                call.page.ifPresent(page -> observe(observed, page));
            }
            Map<String, Integer> ids = new LinkedHashMap<>();
            List<ModelClient.WebSource> sources = new ArrayList<>();
            Map<Integer, Set<Integer>> markers = new TreeMap<>(Comparator.reverseOrder());
            int points = content.codePointCount(0, content.length());
            for (Annotation annotation : this.annotations) {
                if (!annotation.type.equals("url_citation") || annotation.citation.isEmpty()) continue;
                Location citation = annotation.citation.get();
                if (!validUrl(citation.url) || ids.size() >= 64 && !ids.containsKey(citation.url)) continue;
                int id = ids.computeIfAbsent(citation.url, url -> {
                    sources.add(observed.getOrDefault(url, new Page(url, citation.title, "").source()));
                    return sources.size();
                });
                // Provider offsets count Unicode code points; StringBuilder offsets count UTF-16 code units.
                if (citation.start >= 0 && citation.end > citation.start && citation.end <= points)
                    markers.computeIfAbsent(content.offsetByCodePoints(0, citation.end), _ -> new LinkedHashSet<>()).add(id);
            }
            if (sources.isEmpty()) sources.addAll(observed.values());
            StringBuilder answer = new StringBuilder(content);
            markers.forEach((offset, values) -> {
                StringBuilder suffix = new StringBuilder();
                values.forEach(id -> suffix.append('[').append(id).append(']'));
                answer.insert(offset, suffix);
            });
            return new ModelClient.Reply(answer.toString(), List.of(), tokens, sources);
        }
    }

    private static void observe(Map<String, ModelClient.WebSource> sources, Page page) {
        if (sources.size() < 64 && validUrl(page.url)) sources.putIfAbsent(page.url, page.source());
    }

    private static boolean validUrl(String value) {
        if (value.length() > 8192) return false;
        try {
            URI uri = URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static String compact(String value, int limit) {
        String text = value.replaceAll("\\s+", " ").strip();
        return text.codePointCount(0, text.length()) <= limit ? text : text.substring(0, text.offsetByCodePoints(0, limit - 1)) + "…";
    }
}
