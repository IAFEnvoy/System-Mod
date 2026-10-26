package com.iafenvoy.system.agent.routing;

import com.iafenvoy.system.agent.TypedPayload;

public record RouteDecision(TypedPayload<?> input, RouteSource source) {
    public String moduleId() {
        return this.input == null ? "clarify" : this.input.type().id().toString();
    }
}
