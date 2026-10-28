package com.iafenvoy.system.data.condition;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.iafenvoy.system.assistant.AssistantServer;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NonNull;

public record HasModuleCondition(Identifier module) implements EntityCondition {
    public static final MapCodec<HasModuleCondition> CODEC = Identifier.CODEC.fieldOf("module").xmap(HasModuleCondition::new, HasModuleCondition::module);

    @Override
    public @NonNull MapCodec<HasModuleCondition> codec() {
        return CODEC;
    }

    @Override
    public boolean test(EntityConditionContext context) {
        if (!(context.entity() instanceof ServerPlayer player)) return false;
        AssistantServer runtime = AssistantServer.get(player.level().getServer());
        return runtime != null && runtime.moduleManager().hasModule(player.getUUID(), this.module);
    }
}
