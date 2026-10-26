package com.iafenvoy.system.agent.builtin.tool;

import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.input.EmptyInput;
import com.iafenvoy.system.agent.builtin.output.PlayerSnapshotOutput;
import com.iafenvoy.system.runtime.server.MinecraftTools;
import java.util.concurrent.CompletionStage;

/** Reads one current player snapshot on the bounded game-thread queue. */
public record PlayerQueryTool(MinecraftTools game) implements ToolRegistry.Handler<EmptyInput, PlayerSnapshotOutput> {
    public ToolRegistry.Tool<EmptyInput, PlayerSnapshotOutput> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.PLAYER, false, true, true, this);
    }

    @Override
    public CompletionStage<PlayerSnapshotOutput> execute(ExecutionContext context, EmptyInput input) {
        return this.game.onPlayer(context, player -> new PlayerSnapshotOutput(player.getName().getString(), player.getHealth(), player.getMaxHealth(),
                player.experienceLevel, player.level().dimension().identifier().toString(),
                player.level().getServer().getPlayerList().getPlayers().stream().limit(64).map(online -> online.getName().getString()).toList(),
                player.clientInformation().language()));
    }
}
