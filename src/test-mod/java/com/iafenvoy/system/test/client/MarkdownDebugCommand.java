package com.iafenvoy.system.test.client;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.data.output.ChatOutput;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;
import com.iafenvoy.system.registry.SystemClientModules;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Development-only preview; uses the normal chat card without a server request, model or speech call. */
@Mod(value = "system_test", dist = Dist.CLIENT)
public final class MarkdownDebugCommand implements ClientModule {
    private static final Identifier SAMPLE = Identifier.fromNamespaceAndPath("system_test", "debug/markdown.md");

    public MarkdownDebugCommand() {
        SystemClientModules.local(Identifier.fromNamespaceAndPath("system_test", "debug"), this);
    }

    @Override
    public void register(ClientModuleContext module) {
        module.command(MarkdownDebugCommand::show, "debug md");
    }

    private static void show() {
        try (InputStream stream = Minecraft.getInstance().getResourceManager().open(SAMPLE)) {
            String markdown = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            AgentResult result = AgentResult.data("system.result.chat", new ChatOutput(markdown));
            // Local results stay in the transcript; silent prevents automatic speech for this long preview.
            AssistantClient.local(result.withPresentation(result.messageKey(), result.text(), result.cards(), true));
        } catch (IOException error) {
            SystemMod.LOGGER.warn("Unable to load development Markdown sample {}", SAMPLE, error);
            AssistantClient.local(AgentResult.ok("system_test.debug.md_failed"));
        }
    }
}
