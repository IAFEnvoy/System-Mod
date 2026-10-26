package com.iafenvoy.system.agent.builtin;

import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.builtin.input.EmptyInput;
import com.iafenvoy.system.agent.builtin.input.EntitlementInput;
import com.iafenvoy.system.agent.builtin.output.CreditReceiptOutput;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.agent.store.PlayerData;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds reward delivery actions together with daily sign-in and pending order status.
 */
public final class InboxModule extends BuiltinModule<EmptyInput> {
    public InboxModule() {
        super(ResultTypes.INBOX_ACTION, "Read sign-in status, available reward packages and my reward inbox.",
                EmptyInput.CODEC, List.of(), true, false, ExecutionPolicy.quick(2, 0));
    }

    @Override
    public ModuleRouting<EmptyInput> routing() {
        return ModuleRouting.<EmptyInput>defaults()
                .command(AgentCommand.literal(EmptyInput.INSTANCE, "inbox", "收件箱"))
                .semantic(RoutingProfile.read(text -> EmptyInput.INSTANCE,
                        List.of("我今天签到了吗", "查看我的收件箱", "现在有哪些待领取奖励", "看一下签到状态和补给", "查看收件箱", "查看签到状态"),
                        List.of("Have I signed in today?", "Show my reward inbox", "What rewards are waiting for me?", "Check my daily sign-in status", "Show inbox", "Show my inbox")))
                .shortcut("system.action.view_inbox", EmptyInput.INSTANCE);
    }

    @Override
    public AgentResult execute(ExecutionContext context, EmptyInput input) {
        String day = LocalDate.now(ZoneId.of(context.settings().timeZone())).toString();
        return context.store().read(context.player(), data -> {
            List<AgentResult.Card> cards = new ArrayList<>();
            for (PlayerData.Delivery delivery : data.inbox.values().stream().filter(value -> !"DELIVERED".equals(value.state)).limit(32).toList()) {
                List<AgentResult.Action> actions = "PENDING".equals(delivery.state)
                        ? List.of(new AgentResult.Action("system.action.deliver", ResultTypes.DELIVER_ACTION, new EntitlementInput(delivery.id))) : List.of();
                cards.add(new AgentResult.Card("system.result.reward", ResultTypes.ENTITLEMENT.of(new CreditReceiptOutput(delivery.id, delivery.reward, delivery.state)), actions));
            }
            cards.add(new AgentResult.Card("system.result.sign_in_status", ResultTypes.SIGN_IN_STATUS.of(new ResultTypes.SignInStatus(day, data.signIns.containsKey(day),
                    context.catalog().rewards(), data.saveHistory, data.externalModel)), List.of()));
            for (PlayerData.Purchase purchase : data.purchases.values().stream().filter(value -> !"COMPLETED".equals(value.state) && !"FAILED".equals(value.state)).limit(8).toList())
                cards.add(new AgentResult.Card("system.result.shop_order", ResultTypes.SHOP_ORDER.of(new ResultTypes.ShopOrder(purchase.id, purchase.offer.label(), purchase.state, purchase.error)), List.of()));
            return new AgentResult("SUCCEEDED", "system.result.inbox", "", cards);
        });
    }
}
