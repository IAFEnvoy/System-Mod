package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.agent.CardContext;
import com.iafenvoy.system.agent.CardData;
import com.iafenvoy.system.agent.CardFormatting;
import com.iafenvoy.system.agent.store.PlayerData;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

/**
 * Built-in result data and its serialization codec.
 */
public record RewardOutput(PlayerData.Reward reward) implements CardData {
    public static final MapCodec<RewardOutput> CODEC = PlayerData.Reward.CODEC.fieldOf("reward").xmap(RewardOutput::new, RewardOutput::reward);
    public static final Identifier TEMPLATE = Identifier.parse("system:reward");

    @Override
    public void buildContext(CardContext.Builder builder, CardFormatting formatting) {
        formatting.rewardParameters(builder, "", this.reward());
    }

    @Override
    public MapCodec<RewardOutput> codec() {
        return CODEC;
    }

    @Override
    public Identifier template() {
        return TEMPLATE;
    }
}
