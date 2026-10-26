package com.iafenvoy.system.event;

import com.iafenvoy.system.agent.AgentEngine;
import net.neoforged.bus.api.Event;

/**
 * Trusted mods may register modules/tools here before player requests are accepted.
 */
public final class AssistantRegistrationEvent extends Event {
    private final AgentEngine engine;

    public AssistantRegistrationEvent(AgentEngine engine) {
        this.engine = engine;
    }

    public AgentEngine engine() {
        return this.engine;
    }
}
