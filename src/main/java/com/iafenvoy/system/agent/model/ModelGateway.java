package com.iafenvoy.system.agent.model;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The single player-attributed entry point for network inference and usage accounting.
 */
public final class ModelGateway implements AutoCloseable {
    private final ModelClient client;
    private volatile ModelClient.UsageListener usageListener = (player, usage, successful, time) -> {
    };

    public ModelGateway(ModelClient client) {
        this.client = Objects.requireNonNull(client);
    }

    public void onUsage(ModelClient.UsageListener listener) {
        this.usageListener = Objects.requireNonNull(listener);
    }

    public ModelClient.Reply complete(UUID player, List<ModelClient.Message> messages, int maxOutputTokens, Duration timeout) throws Exception {
        Objects.requireNonNull(player);
        ModelClient.Usage[] usage = {ModelClient.Usage.UNKNOWN};
        boolean successful = false;
        try {
            ModelClient.Reply reply = this.client.complete(messages, maxOutputTokens, timeout, value -> usage[0] = Objects.requireNonNull(value));
            successful = true;
            return reply;
        } finally {
            // Accounting survives revocation and cancellation of the originating workflow.
            this.usageListener.record(player, usage[0], successful, System.currentTimeMillis());
        }
    }

    @Override
    public void close() {
        this.client.close();
    }
}
