package com.iafenvoy.saturn.data.context.condition;

import com.iafenvoy.saturn.data.context.Context;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import net.minecraft.world.entity.Entity;

public class EntityConditionContext extends Context {
    private final Entity entity;
    private final FormulaContext formula;

    public EntityConditionContext(Entity entity, FormulaContext formula) {
        this.entity = entity;
        this.formula = formula;
    }

    public Entity entity() {
        return this.entity;
    }

    @Override
    public FormulaContext formula() {
        return this.formula;
    }
}
