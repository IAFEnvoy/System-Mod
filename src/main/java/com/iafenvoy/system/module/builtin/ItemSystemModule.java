package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.agent.AgentModule;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.input.ItemQueryInput;
import com.iafenvoy.system.agent.builtin.item.ItemQueryModule;
import com.iafenvoy.system.agent.builtin.item.ItemQueryTool;
import com.iafenvoy.system.agent.builtin.output.ItemPageOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.SharedModule;
import com.iafenvoy.system.module.client.ClientModuleContext;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class ItemSystemModule implements SharedModule {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("system", "items");

    @Override
    public List<AgentModule<?>> workflows(ModuleContext context) {
        ToolRegistry.Tool<ItemQueryInput, ItemPageOutput> items = context.tool(BuiltinTools.ITEMS, () -> new ItemQueryTool(context.game()).tool());
        return List.of(new ItemQueryModule(items));
    }

    @Override
    public void register(ClientModuleContext module) {
        ClientHooks.register(module);
    }

    // The dedicated server loads the shared module, but never resolves these UI implementations.
    private static final class ClientHooks {
        private static void register(ClientModuleContext module) {
            module.workspace("items", "nav_items", workspace -> {
                workspace.labels("nav_items", "item_search");
                workspace.input("item_query", "system.ui.item_placeholder", "item_search");
                workspace.click("nav_items", () -> workspace.show("items"));
                workspace.click("item_search", () -> {
                    String query = workspace.query("item_query");
                    if (!query.isBlank())
                        AssistantClient.send(new ItemQueryInput(query, 0), "");
                });
            });
        }
    }
}
