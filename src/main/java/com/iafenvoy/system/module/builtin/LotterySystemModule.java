package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.agent.AgentModule;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.commerce.CommercePreviewTool;
import com.iafenvoy.system.agent.builtin.input.CommercePreviewInput;
import com.iafenvoy.system.agent.builtin.input.LotteryQueryInput;
import com.iafenvoy.system.agent.builtin.lottery.LotteryDrawModule;
import com.iafenvoy.system.agent.builtin.lottery.LotteryDrawTool;
import com.iafenvoy.system.agent.builtin.lottery.LotteryModule;
import com.iafenvoy.system.agent.builtin.lottery.LotteryPreviewModule;
import com.iafenvoy.system.agent.builtin.output.CommercePreviewOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.commerce.PurchaseType;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.module.*;
import com.iafenvoy.system.module.builtin.storage.CommerceDataStorage;
import com.iafenvoy.system.module.client.ClientModuleContext;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class LotterySystemModule implements SharedModule {
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

    @Override
    public void register(ClientModuleContext module) {
        ClientHooks.register(module);
    }

    // The dedicated server loads the shared module, but never resolves these UI implementations.
    private static final class ClientHooks {
        private static void register(ClientModuleContext module) {
            module.workspace("lottery", "nav_lottery", workspace -> {
                workspace.labels("nav_lottery", "lottery_search", "lottery_refresh", "lottery_text");
                workspace.input("lottery_query", "system.ui.lottery_placeholder", "lottery_search");
                workspace.click("nav_lottery", () -> {
                    workspace.show("lottery");
                    AssistantClient.send(new LotteryQueryInput("", 0), "");
                });
                workspace.click("lottery_refresh", () -> AssistantClient.send(new LotteryQueryInput("", 0), ""));
                workspace.click("lottery_search", () -> AssistantClient.send(new LotteryQueryInput(workspace.query("lottery_query"), 0), ""));
            });
        }
    }
}
