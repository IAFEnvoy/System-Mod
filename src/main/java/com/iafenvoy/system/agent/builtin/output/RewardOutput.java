package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.agent.store.PlayerData;
import com.mojang.serialization.Codec;

/** Built-in result data and its serialization codec. */
public record RewardOutput(PlayerData.Reward reward) {
    public static final Codec<RewardOutput> CODEC = PlayerData.Reward.CODEC.fieldOf("reward").xmap(RewardOutput::new, RewardOutput::reward).codec();
}
