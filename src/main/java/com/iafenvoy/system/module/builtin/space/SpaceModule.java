package com.iafenvoy.system.module.builtin.space;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.command.AgentCommand;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.execution.ExecutionPolicy;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.input.SpaceInput;
import com.iafenvoy.system.data.module.SystemModule;
import com.iafenvoy.system.data.output.SpaceOutput;
import com.iafenvoy.system.data.result.ShopOrder;
import com.iafenvoy.system.module.ModuleData;
import com.iafenvoy.system.module.builtin.BuiltinTools;
import com.iafenvoy.system.module.builtin.storage.CommerceDataStorage;
import net.minecraft.core.Registry;

import java.util.ArrayList;
import java.util.List;

/**
 * The space is independent of sign-in, commerce and the kinds of content stored in it.
 */
public final class SpaceModule extends AbstractAgentModule<SpaceInput> {
    private final Registry<SystemModule> modules;

    public SpaceModule(ToolRegistry.Tool<SpaceInput, SpaceOutput> query, Registry<SystemModule> modules) {
        super(SpaceInput.CODEC, "View my system space and stored contents, quantities and withdrawal actions.", List.of(query), true, false, ExecutionPolicy.quick(0, 0));
        this.modules = modules;
    }

    @Override
    public ModuleRouting<SpaceInput> routing() {
        return ModuleRouting.<SpaceInput>defaults()
                .button(_ -> SpaceInput.INSTANCE)
                .command(AgentCommand.literal(SpaceInput.INSTANCE, "space", "系统空间", "inbox", "收件箱", "系统背包"))
                .semantic(RoutingProfile.read(_ -> SpaceInput.INSTANCE,
                        List.of("打开系统空间", "查看系统空间", "查看我的系统背包", "查看我的收件箱", "现在有哪些待领取奖励"),
                        List.of("Open system space", "Show my system storage", "Show my reward inbox", "What rewards are waiting for me?")))
                .shortcut("system.action.view_inbox", SpaceInput.INSTANCE);
    }

    @Override
    public AgentResult execute(ExecutionContext context, SpaceInput input) throws Exception {
        List<CardUpdate> cards = new ArrayList<>();
        cards.add(CardUpdate.show(context.tool(BuiltinTools.SPACE_QUERY, "space", input)));
        // Unsettled purchases remain visible without exposing their locked contents as withdrawable entries.
        cards.addAll(context.store().read(context.player(), data -> ModuleData.values(data, CommerceDataStorage.class, this.modules)
                .flatMap(CommerceDataStorage::streamPurchases).filter(purchase -> !"COMPLETED".equals(purchase.state) && !"FAILED".equals(purchase.state))
                .limit(8).map(purchase -> CardUpdate.show(new ShopOrder(purchase.id, purchase.order.label(), purchase.state, purchase.error))).toList()));
        return new AgentResult(AgentResult.Status.SUCCEEDED, "system.result.inbox", "", cards);
    }
}
