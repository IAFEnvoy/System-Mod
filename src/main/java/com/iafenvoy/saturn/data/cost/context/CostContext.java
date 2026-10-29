package com.iafenvoy.saturn.data.cost.context;

import com.iafenvoy.saturn.util.formula.FormulaContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * What a fee is priced and paid against: who pays, the formula it is evaluated with, and which container its item
 * amounts come out of.
 *
 * <p>{@link #data} is the extension point for a dependent mod's own payment channel. Saturn charges items itself; any
 * other store - a world pool, a bank, a script - is named by the caller under a key of its own and read back by the
 * {@code Cost} implementation that charges it:
 * <pre>{@code
 * CostContext context = base.with("mymod:aura_source", source);
 * // and, in the Cost implementation:
 * Object source = context.get("mymod:aura_source");
 * }</pre>
 * The values are carried, never inspected: a key Saturn does not know is nobody's business here.
 */
public record CostContext(@Nullable LivingEntity payer, FormulaContext formula, String origin,
                          @Nullable Container itemTarget, Map<String, Object> data) {
    public CostContext {
        data = Map.copyOf(data);
    }

    public HolderLookup.Provider registries() {
        return this.formula.registries();
    }

    public static CostContext of(@Nullable LivingEntity payer, FormulaContext formula, String origin) {
        return new CostContext(payer, formula, origin, null, Map.of());
    }

    public static CostContext of(@Nullable LivingEntity payer, String origin) {
        return of(payer, payer == null ? FormulaContext.EMPTY : FormulaContext.of(payer), origin);
    }

    public CostContext withPayer(@Nullable LivingEntity payer) {
        return new CostContext(payer, this.formula, this.origin, this.itemTarget, this.data);
    }

    public CostContext withFormula(FormulaContext formula) {
        return new CostContext(this.payer, formula, this.origin, this.itemTarget, this.data);
    }

    public CostContext withItemTarget(Container itemTarget) {
        return new CostContext(this.payer, this.formula, this.origin, itemTarget, this.data);
    }

    /**
     * Carries one more value for a payment channel Saturn does not know, replaced if the key is already there.
     */
    public CostContext with(String key, Object value) {
        Map<String, Object> extended = new LinkedHashMap<>(this.data);
        extended.put(key, value);
        return new CostContext(this.payer, this.formula, this.origin, this.itemTarget, extended);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key) {
        return Optional.ofNullable((T) this.data.get(key));
    }

    public @Nullable Player player() {
        return this.payer instanceof Player player ? player : null;
    }
}
