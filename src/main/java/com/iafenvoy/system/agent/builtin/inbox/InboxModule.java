package com.iafenvoy.system.agent.builtin.inbox;

import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.builtin.BuiltinModule;
import com.iafenvoy.system.agent.builtin.input.InboxInput;
import com.iafenvoy.system.agent.builtin.output.CreditReceiptOutput;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.card.CardUpdate;
import com.iafenvoy.system.config.file.AgentConfig;
import com.iafenvoy.system.data.SystemModule;
import com.iafenvoy.system.module.ModuleData;
import com.iafenvoy.system.module.builtin.InboxSystemModule;
import com.iafenvoy.system.module.builtin.SignInSystemModule;
import com.iafenvoy.system.module.builtin.storage.CommerceDataStorage;
import net.minecraft.core.Registry;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Builds reward delivery actions together with daily sign-in and pending order status.
 */
public final class InboxModule extends BuiltinModule<InboxInput> {
    private final Registry<SystemModule> modules;

    public InboxModule(Registry<SystemModule> modules) {
        super(InboxInput.CODEC, "Read sign-in status, available reward packages and my reward inbox.", List.of(), true, false, ExecutionPolicy.quick(2, 0));
        this.modules = modules;
    }

    @Override
    public ModuleRouting<InboxInput> routing() {
        return ModuleRouting.<InboxInput>defaults()
                .button(_ -> InboxInput.INSTANCE)
                .command(AgentCommand.literal(InboxInput.INSTANCE, "inbox", "收件箱"))
                .semantic(RoutingProfile.read(text -> InboxInput.INSTANCE,
                        List.of("我今天签到了吗", "查看我的收件箱", "现在有哪些待领取奖励", "看一下签到状态和补给", "查看收件箱", "查看签到状态"),
                        List.of("Have I signed in today?", "Show my reward inbox", "What rewards are waiting for me?", "Check my daily sign-in status", "Show inbox", "Show my inbox")))
                .shortcut("system.action.view_inbox", InboxInput.INSTANCE);
    }

    @Override
    public AgentResult execute(ExecutionContext context, InboxInput input) {
        String day = LocalDate.now(ZoneId.of(AgentConfig.INSTANCE.get().timeZone())).toString();
        return context.store().read(context.player(), data -> {
            List<CardUpdate> cards = new ArrayList<>();
            // Payment-pending rewards stay private; unresolved transactions are shown by their order summaries below.
            for (PlayerData.Delivery delivery : ModuleData.get(data, InboxSystemModule.STORAGE).streamDeliveries()
                    .filter(value -> !Set.of("DELIVERED", "PAYMENT_PENDING", "PAYMENT_UNKNOWN").contains(value.state)).limit(32).toList()) {
                cards.add(CardUpdate.show(new CreditReceiptOutput(delivery.id, delivery.reward, delivery.state)));
            }
            cards.add(CardUpdate.show(new ResultTypes.SignInStatus(day, ModuleData.get(data, SignInSystemModule.STORAGE).hasReward(day),
                    context.availableCatalog().rewards(), data.saveHistory, data.externalModel)));
            for (PlayerData.Purchase purchase : ModuleData.values(data, CommerceDataStorage.class, this.modules)
                    .flatMap(CommerceDataStorage::streamPurchases).filter(value -> !"COMPLETED".equals(value.state) && !"FAILED".equals(value.state)).limit(8).toList())
                cards.add(CardUpdate.show(new ResultTypes.ShopOrder(purchase.id, purchase.order.label(), purchase.state, purchase.error)));
            return new AgentResult(AgentResult.Status.SUCCEEDED, "system.result.inbox", "", cards);
        });
    }
}
