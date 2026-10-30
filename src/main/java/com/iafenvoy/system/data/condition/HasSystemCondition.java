package com.iafenvoy.system.data.condition;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.iafenvoy.system.assistant.AssistantServer;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public record HasSystemCondition(Optional<ResourceLocation> system) implements EntityCondition {
    public static final MapCodec<HasSystemCondition> CODEC = ResourceLocation.CODEC.optionalFieldOf("system").xmap(HasSystemCondition::new, HasSystemCondition::system);

    @Override
    public @NotNull MapCodec<HasSystemCondition> codec() {
        return CODEC;
    }

    @Override
    public boolean test(@NotNull EntityConditionContext context) {
        if (!(context.entity() instanceof ServerPlayer player)) return false;
        AssistantServer runtime = AssistantServer.get(player.level().getServer());
        return runtime != null && runtime.moduleManager().hasSystem(player.getUUID())
                && this.system.map(id -> runtime.moduleManager().system(player.getUUID()).filter(id::equals).isPresent()).orElse(true);
    }
}
