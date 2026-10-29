package com.iafenvoy.saturn.data.storage;

import com.iafenvoy.saturn.util.formula.FormulaContext;
import net.minecraft.world.entity.LivingEntity;

/**
 * What a stored value is handed when it ticks: the body it belongs to, the formula it is evaluated with, and the
 * tick it is running on. It says nothing about what kind of definition the value describes, which is what makes a
 * state kind usable for any content a holder addresses rather than only for abilities.
 */
public record DataStorageContext(LivingEntity holder, FormulaContext formula, long gameTime) {
}
