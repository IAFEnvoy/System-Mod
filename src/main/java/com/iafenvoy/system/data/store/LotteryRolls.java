package com.iafenvoy.system.data.store;

import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.data.space.SpaceContent;
import com.iafenvoy.system.data.space.SpaceStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable individual results and the resulting pity state are selected once before payment.
 */
public record LotteryRolls(List<Roll> rolls, int misses) {
    public static final Codec<LotteryRolls> CODEC = RecordCodecBuilder.create(i -> i.group(
            Roll.CODEC.listOf(1, 100).fieldOf("rolls").forGetter(LotteryRolls::rolls),
            Codec.INT.fieldOf("misses").forGetter(LotteryRolls::misses)
    ).apply(i, LotteryRolls::new));

    public LotteryRolls {
        rolls = List.copyOf(rolls);
    }

    public record Roll(PersistHolder<Reward> reward, boolean guaranteed) {
        public static final Codec<Roll> CODEC = RecordCodecBuilder.create(i -> i.group(
                PersistHolder.codec(Reward.CODEC).fieldOf("reward").forGetter(Roll::reward),
                Codec.BOOL.fieldOf("guaranteed").forGetter(Roll::guaranteed)
        ).apply(i, Roll::new));
    }

    /**
     * Merge identical contents, including ordinary items, without losing per-draw receipts.
     */
    public PersistHolder<Reward> bundle(Identifier pool, Component label) {
        if (this.rolls.size() == 1) return this.rolls.getFirst().reward();
        Map<SpaceContent, Long> contents = new LinkedHashMap<>();
        try {
            for (Roll roll : this.rolls)
                for (SpaceStack stack : roll.reward().value().stacks())
                    contents.merge(stack.content(), stack.amount(), Math::addExact);
        } catch (ArithmeticException error) {
            throw new AgentException(AgentFailure.INVALID_REWARD, error);
        }
        if (contents.size() > 4096) throw new AgentException(AgentFailure.RESULT_TOO_LARGE);
        return new PersistHolder<>(pool, new Reward(Component.translatable("system.lottery.batch_reward", label, this.rolls.size()),
                List.of(), contents.entrySet().stream().map(entry -> new SpaceStack(entry.getKey(), entry.getValue())).toList()));
    }
}
