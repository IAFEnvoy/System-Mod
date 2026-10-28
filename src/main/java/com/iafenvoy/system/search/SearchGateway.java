package com.iafenvoy.system.search;

import com.iafenvoy.system.config.file.SearchPricingConfig;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Predicate;

/**
 * Every player-attributed request is accounted for even if its workflow is later cancelled or revoked.
 */
public final class SearchGateway implements AutoCloseable {
    private final SearchProvider provider;
    private volatile UsageListener listener = (player, fetch, successful, billable, unknown, nanoYuan, time) -> {
    };

    public SearchGateway(SearchProvider provider) {
        this.provider = Objects.requireNonNull(provider);
    }

    public void onUsage(UsageListener listener) {
        this.listener = Objects.requireNonNull(listener);
    }

    public SearchProvider.Response search(UUID player, SearchProvider.Request request, Duration timeout) throws Exception {
        return this.call(player, false, () -> this.provider.search(request, timeout), response -> !response.results().isEmpty());
    }

    public SearchProvider.Page fetch(UUID player, SearchProvider.FetchRequest request, Duration timeout) throws Exception {
        return this.call(player, true, () -> this.provider.fetch(request, timeout), page -> !page.content().isBlank());
    }

    private <T> T call(UUID player, boolean fetch, Callable<T> request, Predicate<T> billableResult) throws Exception {
        Objects.requireNonNull(player);
        long price = SearchPricingConfig.INSTANCE.get().nanoYuan(fetch);
        boolean successful = false, billable = false, unknown = false;
        try {
            T result = request.call();
            successful = true;
            billable = billableResult.test(result);
            return result;
        } catch (SearchException error) {
            unknown = switch (error.code()) {
                case "SEARCH_TIMEOUT", "SEARCH_UNAVAILABLE", "SEARCH_INVALID_RESPONSE" -> true;
                default -> false;
            };
            throw error;
        } catch (Exception error) {
            unknown = true;
            throw error;
        } finally {
            this.listener.record(player, fetch, successful, billable, unknown, billable ? price : 0, System.currentTimeMillis());
        }
    }

    @FunctionalInterface
    public interface UsageListener {
        void record(UUID player, boolean fetch, boolean successful, boolean billable, boolean unknown, long nanoYuan, long time);
    }

    @Override
    public void close() {
        this.provider.close();
    }
}
