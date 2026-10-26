package com.iafenvoy.system.agent.routing;

/**
 * Routing provenance is independent of how many model calls a workflow makes.
 */
public enum RouteSource {
    STRUCTURED, COMMAND, SEMANTIC, ROUTE_CACHE, LLM, EVENT;

    public boolean automatic() {
        return this == SEMANTIC || this == ROUTE_CACHE || this == LLM;
    }
}
