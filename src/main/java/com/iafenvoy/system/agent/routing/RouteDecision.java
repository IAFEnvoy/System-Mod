package com.iafenvoy.system.agent.routing;

import com.iafenvoy.system.agent.AgentAction;

public record RouteDecision(AgentAction input, RouteSource source) {
    public String moduleId() {
        return this.input == null ? "clarify" : this.input.id().toString();
    }
}
