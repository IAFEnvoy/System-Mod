package com.iafenvoy.saturn.data.action.builtin.item;

import com.iafenvoy.saturn.data.action.ItemAction;
import com.iafenvoy.saturn.data.context.action.ItemActionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.component.DataComponentPatch;
import org.jetbrains.annotations.NotNull;

/**
 * Merges a vanilla data-component patch into the acted item stack.
 */
public record MergeComponentsAction(DataComponentPatch components) implements ItemAction {
    public static final MapCodec<MergeComponentsAction> CODEC = DataComponentPatch.CODEC.fieldOf("components").xmap(MergeComponentsAction::new, MergeComponentsAction::components);

    @Override
    public void execute(@NotNull ItemActionContext ctx) {
        ctx.stack().applyComponents(this.components);
    }

    @Override
    public @NotNull MapCodec<MergeComponentsAction> codec() {
        return CODEC;
    }
}
