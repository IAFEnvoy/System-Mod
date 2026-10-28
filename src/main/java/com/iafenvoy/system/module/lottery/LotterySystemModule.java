package com.iafenvoy.system.module.lottery;

import com.iafenvoy.system.agent.AgentModule;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.commerce.CommercePreviewTool;
import com.iafenvoy.system.agent.builtin.input.CommercePreviewInput;
import com.iafenvoy.system.agent.builtin.lottery.LotteryDrawModule;
import com.iafenvoy.system.agent.builtin.lottery.LotteryDrawTool;
import com.iafenvoy.system.agent.builtin.lottery.LotteryModule;
import com.iafenvoy.system.agent.builtin.lottery.LotteryPreviewModule;
import com.iafenvoy.system.agent.builtin.output.CommercePreviewOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.SystemModule;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class LotterySystemModule implements SystemModule {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("system", "lottery");

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public List<AgentModule<?>> workflows(ModuleContext context) {
        ToolRegistry.Tool<CommercePreviewInput, List<CommercePreviewOutput>> preview = context.tool(BuiltinTools.COMMERCE_PREVIEW, () -> new CommercePreviewTool(context.commerce()).tool());
        return List.of(new LotteryModule(preview), new LotteryPreviewModule(preview),
                new LotteryDrawModule(new LotteryDrawTool(context.commerce()).tool()));
    }
}
