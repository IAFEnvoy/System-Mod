package com.iafenvoy.system.module.builtin;

import com.iafenvoy.assistant.module.AgentModule;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.assistant.AdvancementTracker;
import com.iafenvoy.system.assistant.tool.advancement.FeatureUnlockTool;
import com.iafenvoy.system.assistant.tool.advancement.RewardCreditTool;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.module.*;
import com.iafenvoy.system.module.builtin.advancement.AdvancementFollowupModule;
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
    public List<AgentModule<?, ExecutionContext, CardUpdate>> workflows(ModuleContext context) {
        return List.of(new AdvancementFollowupModule(new FeatureUnlockTool().tool(), new RewardCreditTool(context.get(ContextTypes.CATALOGS)).tool()));
    }

    @Override
    public PlayerModule runtime(ModuleContext context) {
        return new AdvancementTracker(context.get(ContextTypes.GAME).server(), context.engine());
    }
}
