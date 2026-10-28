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
import com.iafenvoy.system.agent.commerce.PurchaseType;
import com.iafenvoy.system.module.CommerceDataStorage;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.ModuleStorage;
import com.iafenvoy.system.module.PlayerModule;
import com.iafenvoy.system.module.ServerModule;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class LotterySystemModule implements ServerModule {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("system", "lottery");
    public static final ModuleStorage<CommerceDataStorage> STORAGE = new ModuleStorage<>(ID,
            Identifier.fromNamespaceAndPath("system", "commerce"), CommerceDataStorage.class, CommerceDataStorage.CODEC, CommerceDataStorage::new);

    @Override
    public List<ModuleStorage<?>> storages() {
        return List.of(STORAGE);
    }

    @Override
    public PlayerModule runtime(ModuleContext context) {
        return context.commerce().runtime(PurchaseType.LOTTERY, STORAGE);
    }

    @Override
    public List<AgentModule<?>> workflows(ModuleContext context) {
        ToolRegistry.Tool<CommercePreviewInput, List<CommercePreviewOutput>> preview = context.tool(BuiltinTools.COMMERCE_PREVIEW, () -> new CommercePreviewTool(context.commerce()).tool());
        return List.of(new LotteryModule(preview), new LotteryPreviewModule(preview),
                new LotteryDrawModule(new LotteryDrawTool(context.commerce()).tool()));
    }
}
