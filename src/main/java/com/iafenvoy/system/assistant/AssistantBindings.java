package com.iafenvoy.system.assistant;

import com.iafenvoy.system.agent.AgentEngine;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.data.store.PlayerData;
import com.iafenvoy.system.data.store.SystemBindings;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * Binding transactions and result messages belong to the game assistant; execution invalidation belongs to the engine.
 */
public final class AssistantBindings {
    private final AgentEngine engine;

    public AssistantBindings(AgentEngine engine) {
        this.engine = engine;
    }

    public CompletionStage<AgentResult> grant(UUID player, Identifier system, Consumer<PlayerData> initialize) {
        Objects.requireNonNull(system);
        return this.engine.personalMaintenance(player, () -> this.engine.store().update(player, data -> {
            // Assignment and the one-system check share the player transaction.
            if (data.bound()) return data.systems.current().filter(system::equals).isPresent()
                    ? AgentResult.ok("system.result.already_bound") : AgentResult.error("SYSTEM_ALREADY_BOUND");
            data.systems = SystemBindings.single(system);
            initialize.accept(data);
            return AgentResult.ok("system.result.bound");
        }));
    }

    public CompletionStage<AgentResult> clear(UUID player, boolean reset) {
        return this.engine.invalidatePlayer(player, () -> {
            // Deletion deliberately does not decode malformed data; usage has a separate lifetime.
            this.engine.store().delete(player);
            return AgentResult.ok(reset ? "system.result.reset" : "system.result.revoked");
        });
    }
}
