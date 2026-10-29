package com.iafenvoy.saturn.data.action.builtin.block;

import com.iafenvoy.saturn.data.action.BlockAction;
import com.iafenvoy.saturn.data.action.EntityAction;
import com.iafenvoy.saturn.data.context.action.BlockActionContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Spawns an entity at the acted block position and applies an optional entity action.
 */
public record SpawnEntityAction(EntityType<?> entityType, Optional<CompoundTag> tag,
                                EntityAction entityAction) implements BlockAction {
    public static final MapCodec<SpawnEntityAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity_type").forGetter(SpawnEntityAction::entityType),
            CompoundTag.CODEC.optionalFieldOf("tag").forGetter(SpawnEntityAction::tag),
            EntityAction.optionalCodec("entity_action").forGetter(SpawnEntityAction::entityAction)
    ).apply(i, SpawnEntityAction::new));

    @Override
    public void execute(@NotNull BlockActionContext ctx) {
        Level level = ctx.level();
        BlockPos pos = ctx.pos();
        if (!(level instanceof ServerLevel serverLevel) || !level.isLoaded(pos)) return;
        // 1.21.1 only offers the plain create(Level); the spawn reason is not part of this call.
        Entity entity = this.entityType.create(serverLevel);
        if (entity == null) return;
        // 1.21.1 entity deserialisation still takes a plain CompoundTag.
        this.tag.ifPresent(value -> entity.load(value.copy()));
        entity.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        serverLevel.addFreshEntity(entity);
        this.entityAction.execute(entity, ctx);
    }

    @Override
    public @NotNull MapCodec<SpawnEntityAction> codec() {
        return CODEC;
    }
}
