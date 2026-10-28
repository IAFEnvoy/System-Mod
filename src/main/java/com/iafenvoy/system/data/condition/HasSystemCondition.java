package com.iafenvoy.system.data.condition;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.iafenvoy.system.assistant.AssistantServer;
import com.iafenvoy.system.registry.SystemDataPackRegistries;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

public record HasSystemCondition(Optional<Identifier> system) implements EntityCondition {
    public static final MapCodec<HasSystemCondition> CODEC = Identifier.CODEC.optionalFieldOf("system").xmap(HasSystemCondition::new, HasSystemCondition::system);

    @Override
    public @NonNull MapCodec<HasSystemCondition> codec() {
        return CODEC;
    }

    @Override
    public boolean test(EntityConditionContext context) {
        if (!(context.entity() instanceof ServerPlayer player)) return false;
        AssistantServer runtime = AssistantServer.get(player.level().getServer());
        return runtime != null && runtime.moduleManager().hasSystem(player.getUUID())
                && this.system.map(SystemDataPackRegistries.DEFAULT::equals).orElse(true);
    }
}
