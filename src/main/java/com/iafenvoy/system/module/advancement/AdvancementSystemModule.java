package com.iafenvoy.system.module.advancement;

import com.iafenvoy.system.agent.AgentModule;
import com.iafenvoy.system.agent.builtin.advancement.AdvancementFollowupModule;
import com.iafenvoy.system.agent.builtin.advancement.FeatureUnlockTool;
import com.iafenvoy.system.agent.builtin.advancement.RewardCreditTool;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.SystemModule;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class AdvancementSystemModule implements SystemModule {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("system", "advancement");

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public List<AgentModule<?>> workflows(ModuleContext context) {
        return List.of(new AdvancementFollowupModule(new FeatureUnlockTool().tool(), new RewardCreditTool().tool()));
    }

    @Override
    public void installed(ModuleContext context) {
        context.players(new AdvancementTracker(context.game().server(), context.engine()));
    }
}
