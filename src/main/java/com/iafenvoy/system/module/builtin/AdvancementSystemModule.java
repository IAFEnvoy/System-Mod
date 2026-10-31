package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentModule;
import com.iafenvoy.system.agent.builtin.advancement.AdvancementFollowupModule;
import com.iafenvoy.system.agent.builtin.advancement.FeatureUnlockTool;
import com.iafenvoy.system.agent.builtin.advancement.RewardCreditTool;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.ModuleStorage;
import com.iafenvoy.system.module.PlayerModule;
import com.iafenvoy.system.module.WorkflowModule;
import com.iafenvoy.system.module.builtin.storage.AdvancementDataStorage;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class AdvancementSystemModule implements WorkflowModule {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "advancement");
    public static final MapCodec<AdvancementSystemModule> CODEC = MapCodec.unit(new AdvancementSystemModule());
    public static final ModuleStorage<AdvancementDataStorage> STORAGE = new ModuleStorage<>(ID, Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "advancement"), AdvancementDataStorage.class, AdvancementDataStorage.CODEC, AdvancementDataStorage::new);

    @Override
    public MapCodec<AdvancementSystemModule> codec() {
        return CODEC;
    }

    @Override
    public List<ModuleStorage<?>> storages() {
        return List.of(STORAGE);
    }

    @Override
    public List<AgentModule<?>> workflows(ModuleContext context) {
        return List.of(new AdvancementFollowupModule(new FeatureUnlockTool().tool(), new RewardCreditTool().tool()));
    }

    @Override
    public PlayerModule runtime(ModuleContext context) {
        return new AdvancementTracker(context.game().server(), context.engine());
    }
}
