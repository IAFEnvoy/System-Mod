package com.iafenvoy.system.assistant;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.model.ModelClient;
import com.iafenvoy.system.agent.model.OpenAiCompatibleClient;
import com.iafenvoy.system.config.file.ApiConfig;
import com.iafenvoy.system.config.file.ModelConfig;
import com.iafenvoy.system.registry.SystemServices;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Select the configured service per request; in-flight calls retain their own credential snapshot. */
public final class ConfiguredModelClient implements ModelClient {
    private final Set<ModelClient> active = new HashSet<>();
    private boolean closed;

    private static ModelClient selected() {
        synchronized (ModelConfig.INSTANCE) {
            synchronized (ApiConfig.INSTANCE) {
                return ApiConfig.INSTANCE.get().create("model", SystemServices.MODEL)
                        .orElseGet(() -> {
                            if (ModelConfig.INSTANCE.get().apiKeyRequired())
                                throw new AgentException(AgentFailure.MODEL_NOT_CONFIGURED);
                            return new OpenAiCompatibleClient();
                        });
            }
        }
    }

    @Override
    public boolean configured() {
        synchronized (this) {
            if (this.closed) return false;
        }
        try (ModelClient client = selected()) {
            return client.configured();
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    @Override
    public Reply complete(List<Message> messages, int maxOutputTokens, Duration timeout) throws Exception {
        return this.complete(messages, maxOutputTokens, timeout, _ -> {});
    }

    @Override
    public Reply complete(List<Message> messages, int maxOutputTokens, Duration timeout, Consumer<Usage> usage) throws Exception {
        ModelClient client = selected();
        try (client) {
            synchronized (this) {
                if (this.closed) throw new IllegalStateException("Model client is closed");
                this.active.add(client);
            }
            return client.complete(messages, maxOutputTokens, timeout, usage);
        } finally {
            synchronized (this) {
                this.active.remove(client);
            }
        }
    }

    @Override
    public void close() {
        List<ModelClient> clients;
        synchronized (this) {
            this.closed = true;
            clients = List.copyOf(this.active);
            this.active.clear();
        }
        clients.forEach(ModelClient::close);
    }
}
