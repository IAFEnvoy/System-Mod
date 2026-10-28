package com.iafenvoy.system.module.lottery;

import com.iafenvoy.system.agent.builtin.input.LotteryQueryInput;
import com.iafenvoy.system.agent.builtin.output.PurchaseReceiptOutput;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.module.client.ClientMessage;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;
import com.iafenvoy.system.registry.SystemActionTypes;
import net.minecraft.network.chat.Component;

public final class LotteryClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        module.workspace("lottery", "nav_lottery", workspace -> {
            workspace.labels("nav_lottery", "lottery_search", "lottery_refresh", "lottery_text");
            workspace.input("lottery_query", "system.ui.lottery_placeholder", "lottery_search");
            workspace.click("nav_lottery", () -> {
                workspace.show("lottery");
                AssistantClient.send(SystemActionTypes.LOTTERY_ACTION.get().of(new LotteryQueryInput("", 0)), "");
            });
            workspace.click("lottery_refresh", () -> AssistantClient.send(SystemActionTypes.LOTTERY_ACTION.get().of(new LotteryQueryInput("", 0)), ""));
            workspace.click("lottery_search", () -> AssistantClient.send(SystemActionTypes.LOTTERY_ACTION.get().of(new LotteryQueryInput(workspace.query("lottery_query"), 0)), ""));
        });
        module.message("system.result.lottery_drawn", PurchaseReceiptOutput.class, (_, receipt) -> ClientMessage.speech(
                Component.translatable("system.tts.message.lottery_drawn", com.iafenvoy.saturn.util.MiscUtil.clip(receipt.reward().label(), 128)).getString()));
    }
}
