package com.iafenvoy.system.agent.routing;

import com.iafenvoy.system.agent.AgentAction;
import com.iafenvoy.system.agent.AgentResult;

import java.util.List;

public record RouteDecision(AgentAction input, RouteSource source, List<AgentResult.Action> choices) {
    public RouteDecision {
        choices = List.copyOf(choices);
    }

    public RouteDecision(AgentAction input, RouteSource source) {
        this(input, source, List.of());
    }

    public String moduleId() {
        return this.input == null ? "clarify" : this.input.id().toString();
    }
}
