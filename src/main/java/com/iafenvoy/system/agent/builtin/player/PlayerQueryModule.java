package com.iafenvoy.system.agent.builtin.player;

import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.builtin.BuiltinModule;
import com.iafenvoy.system.agent.builtin.input.EmptyInput;
import com.iafenvoy.system.agent.builtin.input.PlayerQueryInput;
import com.iafenvoy.system.agent.builtin.output.PlayerSnapshotOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;

import java.util.List;

/**
 * Reads the current player snapshot through the registered game adapter.
 */
public final class PlayerQueryModule extends BuiltinModule<PlayerQueryInput> {
    public PlayerQueryModule(ToolRegistry.Tool<EmptyInput, PlayerSnapshotOutput> player) {
        super(PlayerQueryInput.CODEC, "Read my current health, experience and dimension; public online player names only.", List.of(player), true, false, ExecutionPolicy.quick(2, 0));
    }

    @Override
    public ModuleRouting<PlayerQueryInput> routing() {
        return ModuleRouting.<PlayerQueryInput>defaults()
                .button(_ -> PlayerQueryInput.INSTANCE)
                .command(AgentCommand.literal(PlayerQueryInput.INSTANCE, "player", "状态", "玩家"))
                .semantic(RoutingProfile.read(text -> PlayerQueryInput.INSTANCE,
                        List.of("查一下我的状态", "看看我当前的血量和等级", "我现在在哪个维度", "检测宿主状态", "查看我的血量", "查看我的等级", "查看我的位置", "查看血量", "查看维度"),
                        List.of("Show my current status", "Check my health and level", "Which dimension am I in?", "Scan my player stats", "Show my health", "Check my level", "Show my location", "Show health", "Show dimension")))
                .shortcut("system.action.view_player", PlayerQueryInput.INSTANCE);
    }

    @Override
    public AgentResult execute(ExecutionContext context, PlayerQueryInput input) throws Exception {
        return AgentResult.data("system.result.player", context.tool(BuiltinTools.PLAYER, "player", EmptyInput.INSTANCE));
    }
}
