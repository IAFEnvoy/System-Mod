package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.agent.AgentModule;
import com.iafenvoy.system.agent.builtin.input.WebSearchInput;
import com.iafenvoy.system.agent.builtin.search.WebFetchTool;
import com.iafenvoy.system.agent.builtin.search.WebSearchModule;
import com.iafenvoy.system.agent.builtin.search.WebSearchTool;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.SharedModule;
import com.iafenvoy.system.module.client.ClientModuleContext;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Availability enables registration; only a current explicit request may incur search charges.
 */
public final class SearchSystemModule implements SharedModule {
    public static final Identifier ID = Identifier.parse("system:web_search");

    @Override
    public List<AgentModule<?>> workflows(ModuleContext context) {
        return List.of(new WebSearchModule(new WebSearchTool(context.engine().search()).tool(), new WebFetchTool(context.engine().search()).tool()));
    }

    @Override
    public void register(ClientModuleContext module) {
        ClientHooks.register(module);
    }

    // The dedicated server loads the shared module, but never resolves these UI implementations.
    private static final class ClientHooks {
        private static void register(ClientModuleContext module) {
            module.workspace("search", "nav_search", workspace -> {
                workspace.labels("nav_search", "web_search", "web_search_note");
                workspace.input("web_query", "system.ui.web_search_placeholder", "web_search");
                workspace.click("nav_search", () -> workspace.show("search"));
                workspace.click("web_search", () -> {
                    String question = workspace.query("web_query");
                    if (!question.isBlank()) AssistantClient.send(new WebSearchInput(question), question);
                });
            });
        }
    }
}
