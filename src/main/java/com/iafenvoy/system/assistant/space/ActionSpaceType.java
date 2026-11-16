package com.iafenvoy.system.assistant.space;

import com.iafenvoy.saturn.data.IconReference;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.data.space.ActionSpaceContent;
import com.iafenvoy.system.data.space.SpaceContentType;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.chat.Component;

public final class ActionSpaceType implements SpaceContentType<ActionSpaceContent> {
    @Override
    public MapCodec<ActionSpaceContent> codec() {
        return ActionSpaceContent.CODEC;
    }

    @Override
    public Component name(ActionSpaceContent content) {
        return content.name().copy();
    }

    @Override
    public IconReference icon(ActionSpaceContent content) {
        return content.icon();
    }

    @Override
    public String searchId(ActionSpaceContent content) {
        return "system:action";
    }

    @Override
    public int batchSize(ActionSpaceContent content) {
        return 1;
    }

    @Override
    public void prepare(ActionSpaceContent content, long amount, SpaceTransfer transfer) {
        if (amount > 64) throw new AgentException(AgentFailure.RESULT_TOO_LARGE);
        for (int index = 0; index < amount; index++)
            transfer.effect(() -> content.action().execute(transfer.player(), FormulaContext.of(transfer.player())));
    }
}
