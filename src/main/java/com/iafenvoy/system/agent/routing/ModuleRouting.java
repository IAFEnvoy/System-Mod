package com.iafenvoy.system.agent.routing;

import com.iafenvoy.system.agent.AgentCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One declaration supplies commands, local intent matching, model visibility and clarification actions.
 */
public record ModuleRouting<I>(List<AgentCommand<I>> commands, Optional<RoutingProfile<I>> semantic,
                               Optional<Shortcut<I>> shortcut, boolean modelSelectable) {
    public ModuleRouting {
        commands = List.copyOf(commands);
        Objects.requireNonNull(semantic);
        Objects.requireNonNull(shortcut);
    }

    public static <I> ModuleRouting<I> defaults() {
        return new ModuleRouting<>(List.of(), Optional.empty(), Optional.empty(), true);
    }

    public ModuleRouting<I> command(AgentCommand<I> command) {
        List<AgentCommand<I>> commands = new ArrayList<>(this.commands);
        commands.add(command);
        return new ModuleRouting<>(commands, this.semantic, this.shortcut, this.modelSelectable);
    }

    public ModuleRouting<I> semantic(RoutingProfile<I> semantic) {
        return new ModuleRouting<>(this.commands, Optional.of(semantic), this.shortcut, this.modelSelectable);
    }

    public ModuleRouting<I> shortcut(String labelKey, I input) {
        return new ModuleRouting<>(this.commands, this.semantic, Optional.of(new Shortcut<>(labelKey, input)), this.modelSelectable);
    }

    public ModuleRouting<I> structuredOnly() {
        return new ModuleRouting<>(this.commands, this.semantic, this.shortcut, false);
    }

    public record Shortcut<I>(String labelKey, I input) {
        public Shortcut {
            Objects.requireNonNull(labelKey);
            Objects.requireNonNull(input);
        }
    }
}
