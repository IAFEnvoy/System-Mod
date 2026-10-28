package com.iafenvoy.system.module.inbox;

import com.iafenvoy.system.agent.AgentModule;
import com.iafenvoy.system.agent.builtin.inbox.DeliverModule;
import com.iafenvoy.system.agent.builtin.inbox.DeliverTool;
import com.iafenvoy.system.agent.builtin.inbox.InboxModule;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.SystemModule;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.UUID;

public final class InboxSystemModule implements SystemModule {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("system", "inbox");

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public List<AgentModule<?>> workflows(ModuleContext context) {
        return List.of(new InboxModule(), new DeliverModule(new DeliverTool(context.engine(), context.game()).tool()));
    }

    @Override
    public void installed(ModuleContext context) {
        context.players(player -> {
            UUID uuid = player.getUUID();
            return data -> {
                for (PlayerData.Delivery delivery : data.inbox.values())
                    if ("DELIVERING".equals(delivery.state)) delivery.state = "DELIVERY_UNKNOWN";
                for (PlayerData.Purchase purchase : data.purchases.values())
                    if ("PREPARED".equals(purchase.state) && !context.commerce().active(uuid, purchase.id)) {
                        purchase.state = "PAYMENT_UNKNOWN";
                        purchase.error = "EFFECT_UNKNOWN";
                        PlayerData.Delivery delivery = data.inbox.get(purchase.order.type().entitlement(purchase.id));
                        if (delivery != null) delivery.state = "PAYMENT_UNKNOWN";
                    }
            };
        });
    }
}
