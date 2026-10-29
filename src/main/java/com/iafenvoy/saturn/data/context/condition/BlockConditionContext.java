package com.iafenvoy.saturn.data.context.condition;

import com.iafenvoy.saturn.data.context.Context;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class BlockConditionContext extends Context {
    private final Level level;
    private final BlockPos pos;
    private final FormulaContext formula;

    public BlockConditionContext(Level level, BlockPos pos, FormulaContext formula) {
        this.level = level;
        this.pos = pos;
        this.formula = formula;
    }

    public Level level() {
        return this.level;
    }

    public BlockPos pos() {
        return this.pos;
    }

    @Override
    public FormulaContext formula() {
        return this.formula;
    }
}
