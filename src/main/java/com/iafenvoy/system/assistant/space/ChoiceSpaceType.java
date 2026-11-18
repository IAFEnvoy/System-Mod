package com.iafenvoy.system.assistant.space;

import com.iafenvoy.saturn.data.IconReference;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.data.space.ChoiceSpaceContent;
import com.iafenvoy.system.data.space.SpaceContentType;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.chat.Component;

public final class ChoiceSpaceType implements SpaceContentType<ChoiceSpaceContent> {
    @Override
    public MapCodec<ChoiceSpaceContent> codec() {
        return ChoiceSpaceContent.CODEC;
    }

    @Override
    public Component name(ChoiceSpaceContent content) {
        return content.name();
    }

    @Override
    public IconReference icon(ChoiceSpaceContent content) {
        return content.icon();
    }

    @Override
    public String searchId(ChoiceSpaceContent content) {
        return "system:choice";
    }

    @Override
    public int batchSize(ChoiceSpaceContent content) {
        return 1;
    }

    @Override
    public long capacity(ChoiceSpaceContent content, long amount) {
        int largest = content.options().stream().mapToInt(reward -> reward.stacks().size()).max().orElse(1);
        return amount > 4096 / largest ? 4097 : amount * largest;
    }

    @Override
    public void prepareReward(ChoiceSpaceContent content, long amount, SpaceTransfer transfer) {
        // Validate every possible output independently; never execute or arbitrarily select an option.
        content.options().forEach(reward -> {
            SpaceTransfer option = new SpaceTransfer(transfer.player());
            reward.stacks().forEach(stack -> SpaceContentType.of(stack.content()).prepareReward(stack.content(), stack.amount(), option));
        });
    }

    @Override
    public void prepare(ChoiceSpaceContent content, long amount, SpaceTransfer transfer) {
        throw new AgentException(AgentFailure.CHOICE_REQUIRED);
    }
}
