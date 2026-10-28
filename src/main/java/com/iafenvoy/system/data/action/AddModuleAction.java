package com.iafenvoy.system.data.action;

import com.iafenvoy.saturn.data.action.EntityAction;
import com.iafenvoy.saturn.data.context.action.EntityActionContext;
import com.iafenvoy.system.assistant.AssistantServer;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NonNull;

public record AddModuleAction(Identifier module) implements EntityAction {
    public static final MapCodec<AddModuleAction> CODEC = Identifier.CODEC.fieldOf("module").xmap(AddModuleAction::new, AddModuleAction::module);

    @Override
    public @NonNull MapCodec<AddModuleAction> codec() {
        return CODEC;
    }

    @Override
    public void execute(EntityActionContext context) {
        if (context.entity() instanceof ServerPlayer player) {
            AssistantServer runtime = AssistantServer.get(player.level().getServer());
            if (runtime != null) runtime.moduleManager().add(player, this.module);
        }
    }
}
