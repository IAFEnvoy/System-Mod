package com.iafenvoy.saturn.data.information.expression.builtin;

import com.iafenvoy.saturn.data.information.expression.ExpressionValue;
import com.iafenvoy.saturn.data.information.expression.InformationEvaluation;
import com.iafenvoy.saturn.data.information.expression.InformationExpression;
import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.data.information.model.InformationValue;
import com.iafenvoy.saturn.util.DefinitionText;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.Set;

public record RegistryNameExpression(ResourceLocation registry,
                                     InformationExpression value) implements InformationExpression {
    public static final MapCodec<RegistryNameExpression> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceLocation.CODEC.fieldOf("registry").forGetter(RegistryNameExpression::registry),
            InformationExpression.CODEC.fieldOf("value").forGetter(RegistryNameExpression::value)
    ).apply(i, RegistryNameExpression::new));

    @Override
    public MapCodec<RegistryNameExpression> codec() {
        return CODEC;
    }

    @Override
    public ExpressionValue evaluate(InformationEvaluation context) {
        ResourceLocation id = context.evaluate(this.value).decode(ResourceLocation.CODEC);
        ResourceKey<Registry<Object>> key = ResourceKey.createRegistryKey(this.registry);
        Object entry = context.ops() instanceof RegistryOps<?> ops
                ? ops.getter(key).flatMap(getter -> getter.get(ResourceKey.create(key, id))).map(Holder.Reference::value).orElse(null)
                : null;
        Component name;
        if (entry == null) name = Component.literal(id.toString());
        else {
            String translation = switch (entry) {
                case Attribute attribute -> attribute.getDescriptionId();
                case MobEffect effect -> effect.getDescriptionId();
                case Item item -> item.getDescriptionId();
                case Block block -> block.getDescriptionId();
                case EntityType<?> entity -> entity.getDescriptionId();
                default -> DefinitionText.key(this.registry, id);
            };
            name = Component.translatableWithFallback(translation, id.toString());
        }
        return new ExpressionValue.Display(new InformationValue.Text(name));
    }

    @Override
    public Set<EntityValueGetter<?>> getters() {
        return this.value.getters();
    }
}
