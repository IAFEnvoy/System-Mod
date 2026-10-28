package com.iafenvoy.system.agent.routing;

import com.iafenvoy.system.agent.AgentAction;
import com.iafenvoy.system.agent.AgentCommand;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.util.ActionArguments;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * One declaration supplies commands, local intent matching, model visibility and clarification actions.
 */
public record ModuleRouting<I extends AgentAction>(List<AgentCommand<I>> commands, Optional<RoutingProfile<I>> semantic,
                                                   Optional<Shortcut<I>> shortcut, boolean modelSelectable,
                                                   Function<ActionArguments, I> buttonDecoder,
                                                   Optional<Function<String, I>> explicit) {
    public ModuleRouting {
        commands = List.copyOf(commands);
        Objects.requireNonNull(semantic);
        Objects.requireNonNull(shortcut);
        Objects.requireNonNull(explicit);
    }

    public static <I extends AgentAction> ModuleRouting<I> defaults() {
        return new ModuleRouting<>(List.of(), Optional.empty(), Optional.empty(), true, _ -> {
            throw new AgentException("UNSUPPORTED");
        }, Optional.empty());
    }

    public ModuleRouting<I> command(AgentCommand<I> command) {
        List<AgentCommand<I>> commands = new ArrayList<>(this.commands);
        commands.add(command);
        return new ModuleRouting<>(commands, this.semantic, this.shortcut, this.modelSelectable, this.buttonDecoder, this.explicit);
    }

    public ModuleRouting<I> semantic(RoutingProfile<I> semantic) {
        return new ModuleRouting<>(this.commands, Optional.of(semantic), this.shortcut, this.modelSelectable, this.buttonDecoder, this.explicit);
    }

    public ModuleRouting<I> shortcut(String labelKey, I input) {
        return new ModuleRouting<>(this.commands, this.semantic, Optional.of(new Shortcut<>(labelKey, input)), this.modelSelectable, this.buttonDecoder, this.explicit);
    }

    public ModuleRouting<I> structuredOnly() {
        return this.explicitOnly();
    }

    public ModuleRouting<I> explicitOnly() {
        return new ModuleRouting<>(this.commands, this.semantic, this.shortcut, false, this.buttonDecoder, this.explicit);
    }

    public ModuleRouting<I> button(Function<ActionArguments, I> decoder) {
        return new ModuleRouting<>(this.commands, this.semantic, this.shortcut, this.modelSelectable, decoder, this.explicit);
    }

    /**
     * A conservative current-message parser can authorize an explicit-only workflow without an LLM.
     */
    public ModuleRouting<I> explicit(Function<String, I> parser) {
        return new ModuleRouting<>(this.commands, this.semantic, this.shortcut, this.modelSelectable, this.buttonDecoder, Optional.of(parser));
    }

    public record Shortcut<I>(String labelKey, I input) {
        public Shortcut {
            Objects.requireNonNull(labelKey);
            Objects.requireNonNull(input);
        }
    }
}
