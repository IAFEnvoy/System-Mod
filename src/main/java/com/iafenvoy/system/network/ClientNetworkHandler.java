package com.iafenvoy.system.network;

import com.iafenvoy.system.agent.AgentRequest;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.network.payload.AssistantResponseS2CPayload;
import com.iafenvoy.system.network.payload.TokenUsageResponseS2CPayload;
import com.iafenvoy.system.runtime.client.AssistantClient;
import com.iafenvoy.system.screen.AssistantMessages;
import com.iafenvoy.system.screen.gui.AssistantScreen;
import com.iafenvoy.system.screen.gui.AssistantSettingsScreen;
import com.iafenvoy.system.screen.gui.AssistantSimpleScreen;
import com.iafenvoy.system.screen.gui.TokenUsageScreen;
import com.iafenvoy.system.util.CodecIO;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client payload dispatch and decoding; views and local conversation state stay in the client layer. */
public final class ClientNetworkHandler {
    private ClientNetworkHandler() {
    }

    public static void handleAssistantResponse(AssistantResponseS2CPayload response, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (response.id().equals("bound")) {
            AssistantClient.local(AgentResult.ok("system.result.bound"));
            AssistantMessages.binding(minecraft.player == null ? "" : minecraft.player.getName().getString());
            AssistantClient.send(AgentRequest.Control.STATE);
            return;
        }
        if (Set.of("open", "open_simple", "open_full").contains(response.id())) {
            boolean simple = response.id().equals("open_simple") || !response.id().equals("open_full") && AssistantClient.simpleMode();
            AssistantClient.open(simple, "");
            AssistantClient.send(AgentRequest.Control.STATE);
            return;
        }
        if (response.id().equals("revoked")) {
            AssistantClient.clear();
            if (minecraft.screen instanceof AssistantScreen || minecraft.screen instanceof AssistantSimpleScreen || minecraft.screen instanceof TokenUsageScreen
                    || minecraft.screen instanceof AssistantSettingsScreen)
                minecraft.setScreen(null);
            AssistantMessages.result(AgentResult.ok("system.result.revoked"), false);
            return;
        }
        AgentResult result;
        try {
            result = CodecIO.parse(AgentResult.CODEC, response.result(), 65536);
        } catch (RuntimeException error) {
            result = AgentResult.error("INVALID_INPUT");
        }
        AssistantClient.receive(response.id(), result);
    }

    public static void handleTokenUsageResponse(TokenUsageResponseS2CPayload response, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (response.id().equals("open")) {
            if (minecraft.screen instanceof TokenUsageScreen screen) screen.update(response);
            else minecraft.setScreen(new TokenUsageScreen(minecraft.screen, response));
        } else if (minecraft.screen instanceof TokenUsageScreen screen) screen.receive(response);
    }
}
