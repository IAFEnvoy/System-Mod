package com.iafenvoy.system.assistant.tool.player;

import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.assistant.MinecraftTools;
import com.iafenvoy.system.assistant.PlayerInformationView;
import com.iafenvoy.system.data.input.EmptyInput;
import com.iafenvoy.system.data.output.PlayerSnapshotOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.concurrent.CompletionStage;

/**
 * Reads one current player snapshot on the bounded game-thread queue.
 */
public record PlayerQueryTool(MinecraftTools game) implements ToolRegistry.Handler<EmptyInput, PlayerSnapshotOutput> {
    public ToolRegistry.Tool<EmptyInput, PlayerSnapshotOutput> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.PLAYER, false, true, true, this)
                .forReasoning("Read the current player's health, level and game information.");
    }

    @Override
    public CompletionStage<PlayerSnapshotOutput> execute(ExecutionContext context, EmptyInput input) {
        return this.game.onPlayer(context, player -> new PlayerSnapshotOutput(player.getUUID(), player.getName().getString(), player.getHealth(), player.getMaxHealth(),
                player.experienceLevel, player.level().dimension().identifier().toString(),
                player.clientInformation().language(), PlayerInformationView.collect(player)));
    }
}
