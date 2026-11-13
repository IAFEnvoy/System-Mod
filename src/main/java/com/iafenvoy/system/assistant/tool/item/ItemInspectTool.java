package com.iafenvoy.system.assistant.tool.item;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.assistant.MinecraftTools;
import com.iafenvoy.system.data.input.ItemInspectInput;
import com.iafenvoy.system.data.output.ItemInspectionOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.concurrent.CompletionStage;

public record ItemInspectTool(MinecraftTools game) implements ToolRegistry.Handler<ItemInspectInput, ItemInspectionOutput> {
    public ToolRegistry.Tool<ItemInspectInput, ItemInspectionOutput> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.ITEM_INSPECT, false, true, false, this)
                .forReasoning("Inspect the player's current main-hand item, durability and enchantments.");
    }

    @Override
    public CompletionStage<ItemInspectionOutput> execute(ExecutionContext context, ItemInspectInput input) {
        return this.game.onPlayer(context, player -> {
            if (player.getMainHandItem().isEmpty()) throw new AgentException(AgentFailure.EMPTY_HAND);
            return ItemInspectionOutput.of(player.getMainHandItem());
        });
    }
}
