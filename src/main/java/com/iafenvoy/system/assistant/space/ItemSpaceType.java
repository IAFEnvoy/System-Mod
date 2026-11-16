package com.iafenvoy.system.assistant.space;

import com.iafenvoy.saturn.data.IconReference;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.data.space.ItemSpaceContent;
import com.iafenvoy.system.data.space.SpaceContentType;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class ItemSpaceType implements SpaceContentType<ItemSpaceContent> {
    @Override
    public MapCodec<ItemSpaceContent> codec() {
        return ItemSpaceContent.CODEC;
    }

    private static ItemStack stack(ItemSpaceContent content) {
        ItemStack stack = content.item().create();
        if (stack.isEmpty()) throw new AgentException(AgentFailure.INVALID_REWARD);
        return stack;
    }

    @Override
    public Component name(ItemSpaceContent content) {
        return stack(content).getHoverName().copy();
    }

    @Override
    public IconReference icon(ItemSpaceContent content) {
        return new IconReference(Either.right(content.item()));
    }

    @Override
    public String searchId(ItemSpaceContent content) {
        return BuiltInRegistries.ITEM.getKey(content.item().item().value()).toString();
    }

    @Override
    public int batchSize(ItemSpaceContent content) {
        return stack(content).getMaxStackSize();
    }

    @Override
    public void prepare(ItemSpaceContent content, long amount, SpaceTransfer transfer) {
        ItemStack template = stack(content);
        // A full entitlement can contain multiple stacks; quantities never alter the stored template.
        if (amount > 36L * template.getMaxStackSize()) throw new AgentException(AgentFailure.RESULT_TOO_LARGE);
        while (amount > 0) {
            int count = (int) Math.min(amount, template.getMaxStackSize());
            transfer.item(template.copyWithCount(count));
            amount -= count;
        }
    }
}
