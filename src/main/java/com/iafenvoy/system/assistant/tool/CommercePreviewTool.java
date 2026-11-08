package com.iafenvoy.system.assistant.tool;

import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.assistant.commerce.CommerceTransactions;
import com.iafenvoy.system.data.input.CommercePreviewInput;
import com.iafenvoy.system.data.output.CommercePreviewOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

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
