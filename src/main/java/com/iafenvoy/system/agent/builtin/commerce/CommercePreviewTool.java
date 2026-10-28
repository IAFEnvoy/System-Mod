package com.iafenvoy.system.agent.builtin.commerce;

import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.input.CommercePreviewInput;
import com.iafenvoy.system.agent.builtin.output.CommercePreviewOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.commerce.CommerceTransactions;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * One read-only tool evaluates catalog costs without testing or charging the player's stores.
 */
public record CommercePreviewTool(
        CommerceTransactions transactions) implements ToolRegistry.Handler<CommercePreviewInput, List<CommercePreviewOutput>> {
    public ToolRegistry.Tool<CommercePreviewInput, List<CommercePreviewOutput>> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.COMMERCE_PREVIEW, false, true, false, this);
    }

    @Override
    public CompletionStage<List<CommercePreviewOutput>> execute(ExecutionContext context, CommercePreviewInput input) {
        return this.transactions.preview(context, input);
    }
}
