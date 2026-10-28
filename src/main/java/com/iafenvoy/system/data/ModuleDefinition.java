package com.iafenvoy.system.data;

import com.iafenvoy.saturn.data.condition.AlwaysCondition;
import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/** A feature may be unconditional or guarded by any registered Saturn entity condition. */
public record ModuleDefinition(Identifier id, EntityCondition condition) {
    private static final Codec<ModuleDefinition> OBJECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("id").forGetter(ModuleDefinition::id),
            EntityCondition.CODEC.optionalFieldOf("condition", AlwaysCondition.INSTANCE).forGetter(ModuleDefinition::condition)
    ).apply(i, ModuleDefinition::new));
    public static final Codec<ModuleDefinition> CODEC = Codec.withAlternative(OBJECT_CODEC,
            Identifier.CODEC.xmap(ModuleDefinition::new, ModuleDefinition::id));

    public ModuleDefinition(Identifier id) {
        this(id, AlwaysCondition.INSTANCE);
    }
}
