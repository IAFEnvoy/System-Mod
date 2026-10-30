package com.iafenvoy.saturn.util.formula.variable;

import com.iafenvoy.saturn.registry.cache.RegistryCaches;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.iafenvoy.saturn.util.formula.FormulaVariable;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Health, vanilla experience level, one name per resource and per attribute the entity has. Anything a world wants
// to expose beyond that is a formula variable of its own, registered in its own namespace.
public final class EntityVariable implements FormulaVariable {
    private static final Map<EntityType<?>, Set<Holder<Attribute>>> SYNCABLE_ATTRIBUTES = new ConcurrentHashMap<>();

    private final String prefix;
    private final Set<String> prefixes;

    public EntityVariable(String prefix) {
        this.prefix = prefix;
        this.prefixes = Set.of(prefix);
    }

    @Override
    public Set<String> names() {
        return Set.of();
    }

    @Override
    public Set<String> prefixes() {
        return this.prefixes;
    }

    @Override
    public double value(String key, String suffix, FormulaContext context) {
        Entity entity = this.prefix.equals(key) ? context.caster() : context.target();
        if (entity == null) return Double.NaN;
        return switch (suffix) {
            case "health" -> entity instanceof LivingEntity living ? (double) living.getHealth() : Double.NaN;
            case "max_health" -> entity instanceof LivingEntity living ? (double) living.getMaxHealth() : Double.NaN;
            case "level" -> entity instanceof ServerPlayer player ? (double) player.experienceLevel : 0.0D;
            default -> state(entity, suffix);
        };
    }

    private static double state(Entity entity, String field) {
        if (!(entity instanceof LivingEntity living)) return Double.NaN;
        Holder<Attribute> attribute = RegistryCaches.ATTRIBUTE_NAMES.get(field);
        if (attribute != null) {
            AttributeInstance instance = living.getAttributes().getInstance(attribute);
            if (instance == null) return 0.0D;
            if (living.level().isClientSide() && !synchronised(living, attribute)) return 0.0D;
            return instance.getValue();
        }
        return Double.NaN;
    }

    // The client only holds the attributes it needs, so a formula must not read a hidden attribute as if it
    // were up to date.
    private static boolean synchronised(LivingEntity entity, Holder<Attribute> attribute) {
        Set<Holder<Attribute>> syncable = SYNCABLE_ATTRIBUTES.get(entity.getType());
        if (syncable == null) {
            Set<Holder<Attribute>> collected = new HashSet<>();
            for (AttributeInstance instance : entity.getAttributes().getSyncableAttributes())
                collected.add(instance.getAttribute());
            syncable = Set.copyOf(collected);
            SYNCABLE_ATTRIBUTES.put(entity.getType(), syncable);
        }
        return syncable.contains(attribute);
    }
}
