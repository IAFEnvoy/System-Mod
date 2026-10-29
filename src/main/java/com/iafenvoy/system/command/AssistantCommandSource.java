package com.iafenvoy.system.command;

import com.iafenvoy.system.agent.AgentAction;
import net.minecraft.resources.Identifier;

import java.util.function.Predicate;

/**
 * One dispatch owns its input and result; cached command trees never retain a player or request.
 */
public final class AssistantCommandSource {
    private final String input;
    private final Predicate<Identifier> available;
    private AgentAction action;

    public AssistantCommandSource(String input, Predicate<Identifier> available) {
        this.input = input;
        this.available = available;
    }

    public String input() {
        return this.input;
    }

    public boolean available(Identifier id) {
        return this.available.test(id);
    }

    public int action(AgentAction action) {
        this.action = action;
        return 1;
    }

    public AgentAction action() {
        return this.action;
    }
}
