package com.iafenvoy.system.assistant;

import com.iafenvoy.system.config.file.ApiConfig;
import com.iafenvoy.system.config.file.SearchConfig;
import com.iafenvoy.system.registry.SystemServices;
import com.iafenvoy.system.search.SearchException;
import com.iafenvoy.system.search.SearchProvider;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Request-scoped search selection; account changes never alter a running request. */
public final class ConfiguredSearchProvider implements SearchProvider {
    private final Set<SearchProvider> active = new HashSet<>();
    private boolean closed;

    private SearchProvider open() throws SearchException {
        synchronized (SearchConfig.INSTANCE) {
            synchronized (ApiConfig.INSTANCE) {
                SearchProvider provider = ApiConfig.INSTANCE.get().create("search", SystemServices.SEARCH)
                        .orElseThrow(() -> new SearchException("SEARCH_NOT_CONFIGURED"));
                synchronized (this) {
                    if (this.closed) {
                        provider.close();
                        throw new SearchException("SEARCH_NOT_CONFIGURED");
                    }
                    this.active.add(provider);
                }
                return provider;
            }
        }
    }

    @Override
    public boolean configured() {
        try {
            SearchProvider provider = this.open();
            try (provider) {
                return provider.configured();
            } finally {
                this.release(provider);
            }
        } catch (SearchException | RuntimeException unavailable) {
            return false;
        }
    }

    @Override
    public String defaultBaseUrl() {
        return SearchConfig.INSTANCE.get().baseUrl();
    }

    @Override
    public Response search(Request request, Duration timeout) throws Exception {
        SearchProvider provider = this.open();
        try (provider) {
            return provider.search(request, timeout);
        } finally {
            this.release(provider);
        }
    }

    @Override
    public Page fetch(FetchRequest request, Duration timeout) throws Exception {
        SearchProvider provider = this.open();
        try (provider) {
            return provider.fetch(request, timeout);
        } finally {
            this.release(provider);
        }
    }

    private synchronized void release(SearchProvider provider) {
        this.active.remove(provider);
    }

    @Override
    public void close() {
        List<SearchProvider> providers;
        synchronized (this) {
            this.closed = true;
            providers = List.copyOf(this.active);
            this.active.clear();
        }
        providers.forEach(SearchProvider::close);
    }
}
