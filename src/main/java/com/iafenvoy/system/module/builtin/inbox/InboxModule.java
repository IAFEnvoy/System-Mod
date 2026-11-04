package com.iafenvoy.system.module.builtin.inbox;

import com.iafenvoy.saturn.util.HolderHelper;
import com.iafenvoy.system.agent.AbstractAgentModule;
import com.iafenvoy.system.agent.AgentCommand;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ExecutionPolicy;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.assistant.commerce.CommerceCatalogs;
import com.iafenvoy.system.config.file.AgentConfig;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.input.InboxInput;
import com.iafenvoy.system.data.module.SystemModule;
import com.iafenvoy.system.data.output.CreditReceiptOutput;
import com.iafenvoy.system.data.result.ShopOrder;
import com.iafenvoy.system.data.result.SignInStatus;
import com.iafenvoy.system.data.store.Delivery;
import com.iafenvoy.system.data.store.Purchase;
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
public final class InboxModule extends AbstractAgentModule<InboxInput> {
    private final CommerceCatalogs catalogs;
    private final Registry<SystemModule> modules;

    public InboxModule(CommerceCatalogs catalogs, Registry<SystemModule> modules) {
        super(InboxInput.CODEC, "Read sign-in status, available reward packages and my reward inbox.", List.of(), true, false, ExecutionPolicy.quick(2, 0));
        this.catalogs = catalogs;
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
            for (Delivery delivery : ModuleData.get(data, InboxSystemModule.STORAGE).streamDeliveries()
                    .filter(value -> !Set.of("DELIVERED", "PAYMENT_PENDING", "PAYMENT_UNKNOWN").contains(value.state)).limit(32).toList()) {
                cards.add(CardUpdate.show(new CreditReceiptOutput(delivery.id, delivery.reward, delivery.state)));
            }
            cards.add(CardUpdate.show(new SignInStatus(day, ModuleData.get(data, SignInSystemModule.STORAGE).hasReward(day),
                    this.catalogs.available(context.player()).rewards().stream()
                            .map(holder -> holder.value().reward(HolderHelper.id(holder))).toList(),
                    data.saveHistory, data.externalModel)));
            for (Purchase purchase : ModuleData.values(data, CommerceDataStorage.class, this.modules)
                    .flatMap(CommerceDataStorage::streamPurchases).filter(value -> !"COMPLETED".equals(value.state) && !"FAILED".equals(value.state)).limit(8).toList())
                cards.add(CardUpdate.show(new ShopOrder(purchase.id, purchase.order.label(), purchase.state, purchase.error)));
            return new AgentResult(AgentResult.Status.SUCCEEDED, "system.result.inbox", "", cards);
        });
    }
}
