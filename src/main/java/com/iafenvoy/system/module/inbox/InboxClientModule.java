package com.iafenvoy.system.module.inbox;

import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.builtin.input.EmptyInput;
import com.iafenvoy.system.agent.builtin.output.CreditReceiptOutput;
import com.iafenvoy.system.agent.builtin.output.PurchaseReceiptOutput;
import com.iafenvoy.system.agent.builtin.output.RewardOutput;
import com.iafenvoy.system.agent.builtin.output.StateOutput;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;
import net.minecraft.resources.Identifier;

import java.util.Locale;

import static com.iafenvoy.system.screen.gui.AssistantView.rewardParameters;
import static com.iafenvoy.system.screen.gui.AssistantView.tr;

public final class InboxClientModule implements ClientModule {
    @Override
    public Identifier id() {
        return InboxSystemModule.ID;
    }

    @Override
    public void register(ClientModuleContext module) {
        module.workspace("", "nav_inbox", workspace -> {
            workspace.labels("nav_inbox");
            workspace.click("nav_inbox", () -> {
                workspace.show("chat");
                AssistantClient.send(ResultTypes.INBOX_ACTION.of(EmptyInput.INSTANCE), "");
            });
        });
        module.cards().register(RewardOutput.class, (builder, value) -> rewardParameters(builder, value.reward()));
        module.cards().register(CreditReceiptOutput.class, (builder, value) -> rewardParameters(builder, value.reward())
                .put("entitlement_id", value.entitlementId()).put("state", value.state()));
        module.cards().register(PurchaseReceiptOutput.class, (builder, value) -> rewardParameters(builder, value.reward())
                .put("order_id", value.orderId()).put("entitlement_id", value.entitlementId()).put("state", value.state()));
        module.cards().register(ResultTypes.ShopOrder.class, (builder, value) -> builder
                .put("order_id", value.orderId()).put("label", value.label()).put("state", value.state())
                .put("error", value.error().isBlank() ? "" : tr("system.error." + value.error().toLowerCase(Locale.ROOT))));
        module.cards().register(StateOutput.class, (builder, value) -> builder.put("state", value.state()));
    }
}
