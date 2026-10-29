package com.iafenvoy.system.data;

import com.iafenvoy.saturn.data.condition.AlwaysCondition;
import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.util.codec.RegistryCodecs;
import com.iafenvoy.system.registry.SystemResourceKeys;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.util.List;

/**
 * A module ID or tag may be unconditional or guarded by a Saturn entity condition.
 */
public record ModuleDefinition(Either<Holder<SystemModule>, TagKey<SystemModule>> id, EntityCondition condition) {
    private static final Codec<Either<Holder<SystemModule>, TagKey<SystemModule>>> ID_CODEC = RegistryCodecs.holderOrTag(SystemResourceKeys.SYSTEM_MODULE);
    private static final Codec<ModuleDefinition> OBJECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            ID_CODEC.fieldOf("id").forGetter(ModuleDefinition::id),
            EntityCondition.CODEC.optionalFieldOf("condition", AlwaysCondition.INSTANCE).forGetter(ModuleDefinition::condition)
    ).apply(i, ModuleDefinition::new));
    public static final Codec<ModuleDefinition> CODEC = Codec.withAlternative(OBJECT_CODEC, ID_CODEC.xmap(ModuleDefinition::new, ModuleDefinition::id));

    public ModuleDefinition(Either<Holder<SystemModule>, TagKey<SystemModule>> id) {
        this(id, AlwaysCondition.INSTANCE);
    }

    public boolean matches(Registry<SystemModule> registry, ResourceLocation module) {
        return RegistryCodecs.matches(List.of(this.id), registry, module);
    }
}
