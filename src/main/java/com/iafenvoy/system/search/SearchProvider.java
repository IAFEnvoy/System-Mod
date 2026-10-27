package com.iafenvoy.system.search;

import com.iafenvoy.system.util.codec.CodecIO;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Read-only search and page retrieval; no Agent registration or Minecraft state is involved.
 */
public interface SearchProvider extends AutoCloseable {
    Response search(Request request, Duration timeout) throws Exception;

    Page fetch(FetchRequest request, Duration timeout) throws Exception;

    record Request(String query, int count, Optional<String> originalQuestion, String localization,
                   boolean needContent) {
        // These are provider API limits, counted as Unicode code points rather than UTF-16 units.
        private static final Codec<String> QUERY = CodecIO.checked(Codec.STRING, value -> !value.isBlank()
                && value.codePointCount(0, value.length()) <= 128);
        public static final Codec<Request> CODEC = RecordCodecBuilder.create(i -> i.group(
                QUERY.fieldOf("query").forGetter(Request::query),
                Codec.intRange(1, 50).optionalFieldOf("count", 10).forGetter(Request::count),
                QUERY.optionalFieldOf("ori_question").forGetter(Request::originalQuestion),
                Codec.STRING.optionalFieldOf("localization", "").forGetter(Request::localization),
                Codec.BOOL.optionalFieldOf("need_content", false).forGetter(Request::needContent)
        ).apply(i, Request::new));

        public Request(String query) {
            this(query, 10, Optional.empty(), "", false);
        }

        public Request(String query, int count, boolean needContent) {
            this(query, count, Optional.empty(), "", needContent);
        }
    }

    record Result(double rerankScore, String title, String url, String snippet, String summary, String content,
                  Optional<String> siteName, Optional<String> siteIcon, Optional<String> date, int authority) {
        public static final Codec<Result> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("rerank_score").forGetter(Result::rerankScore),
                Codec.STRING.fieldOf("title").forGetter(Result::title),
                Codec.STRING.fieldOf("url").forGetter(Result::url),
                Codec.STRING.fieldOf("snippet").forGetter(Result::snippet),
                Codec.STRING.fieldOf("summary").forGetter(Result::summary),
                Codec.STRING.fieldOf("content").forGetter(Result::content),
                // The API explicitly returns null for unavailable site metadata and publication dates.
                Codec.STRING.lenientOptionalFieldOf("site_name").forGetter(Result::siteName),
                Codec.STRING.lenientOptionalFieldOf("site_icon").forGetter(Result::siteIcon),
                Codec.STRING.lenientOptionalFieldOf("date").forGetter(Result::date),
                Codec.INT.fieldOf("authority").forGetter(Result::authority)
        ).apply(i, Result::new));
    }

    record Response(List<Result> results) {
        public static final Codec<Response> CODEC = Result.CODEC.listOf().fieldOf("results")
                .xmap(Response::new, Response::results).codec();

        public Response {
            results = List.copyOf(results);
        }
    }

    record FetchRequest(String url) {
        public static final Codec<FetchRequest> CODEC = CodecIO.checked(Codec.STRING, FetchRequest::validUrl)
                .fieldOf("url").xmap(FetchRequest::new, FetchRequest::url).codec();

        private static boolean validUrl(String value) {
            try {
                URI uri = URI.create(value);
                return value.codePointCount(0, value.length()) <= 8192 && uri.getHost() != null
                        && uri.getUserInfo() == null && ("https".equalsIgnoreCase(uri.getScheme())
                        || "http".equalsIgnoreCase(uri.getScheme()));
            } catch (IllegalArgumentException error) {
                return false;
            }
        }
    }

    record Page(String title, String content) {
        public static final Codec<Page> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("title", "").forGetter(Page::title),
                Codec.STRING.fieldOf("content").forGetter(Page::content)
        ).apply(i, Page::new));
    }

    @Override
    void close();
}
