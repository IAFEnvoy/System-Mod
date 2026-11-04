package com.iafenvoy.system.agent.builtin.inbox;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ExecutionPolicy;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.BuiltinModule;
import com.iafenvoy.system.agent.builtin.BuiltinTools;
import com.iafenvoy.system.agent.builtin.input.EntitlementInput;
import com.iafenvoy.system.agent.builtin.output.CreditReceiptOutput;
import com.iafenvoy.system.agent.builtin.output.PurchaseReceiptOutput;
import com.iafenvoy.system.agent.builtin.output.RewardOutput;
import com.iafenvoy.system.agent.builtin.output.StateOutput;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.agent.store.Delivery;
import com.iafenvoy.system.agent.store.Reward;
import com.iafenvoy.system.card.CardData;
import com.iafenvoy.system.card.CardUpdate;
import com.iafenvoy.system.module.ModuleData;
import com.iafenvoy.system.module.builtin.InboxSystemModule;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * Delivers one existing entitlement through the game adapter.
 */
public final class DeliverModule extends BuiltinModule<EntitlementInput> {
    public DeliverModule(ToolRegistry.Tool<EntitlementInput, StateOutput> deliver) {
        super(EntitlementInput.CODEC, "Deliver an existing reward entitlement obtained from the inbox; never invent entitlement IDs.", List.of(deliver), true, false, ExecutionPolicy.quick(1, 1));
    }

    @Override
    public ModuleRouting<EntitlementInput> routing() {
        return ModuleRouting.<EntitlementInput>defaults()
                .button(a -> new EntitlementInput(a.required("entitlement", "entitlementId"), a.optional("kind", "kind"),
                        a.optional("order", "order"), a.optional("pool", "pool"), a.optional("summary", "summary")))
                .semantic(RoutingProfile.hints(
                        List.of("把收件箱的东西发给我", "领取我的奖励物品", "把补给放进背包", "给我发放已入账奖励"),
                        List.of("Deliver my inbox reward", "Put my supplies in my inventory", "Claim my reward items", "Give me the credited supplies")));
    }

    @Override
    public AgentResult execute(ExecutionContext context, EntitlementInput input) throws Exception {
        StateOutput state = context.tool(BuiltinTools.DELIVER, "deliver", input);
        CardData receipt = this.receipt(context, input);
        if (receipt == null) return AgentResult.data("system.result.delivery", state);
        // The finished receipt replaces its own card in place: no separate state card, and the claim button turns
        // into a delivered marker.
        return new AgentResult(AgentResult.Status.SUCCEEDED, "system.result.delivery", "",
                List.of(CardUpdate.show(receipt)), true);
    }

    /**
     * Rebuilds the originating card from the stored reward and the delivery button's own arguments.
     */
    private CardData receipt(ExecutionContext context, EntitlementInput input) {
        Reward reward = context.store().read(context.player(), data -> {
            Delivery delivery = ModuleData.get(data, InboxSystemModule.STORAGE).getDelivery(input.entitlementId());
            return delivery == null ? null : delivery.reward;
        });
        if (reward == null) return null;
        return switch (input.kind().orElse("")) {
            case "purchase" -> new PurchaseReceiptOutput(input.order().orElse(""), input.entitlementId(), reward,
                    "DELIVERED", input.pool().flatMap(pool -> Optional.ofNullable(Identifier.tryParse(pool))));
            case "reward" -> new RewardOutput(reward, Optional.empty(),
                    Component.literal(input.summary().orElse("")), "").asDelivered();
            default -> new CreditReceiptOutput(input.entitlementId(), reward, "DELIVERED");
        };
    }
}
