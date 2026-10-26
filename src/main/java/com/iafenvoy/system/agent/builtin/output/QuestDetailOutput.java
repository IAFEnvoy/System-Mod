package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.util.codec.DispatchRegistry;
import com.mojang.serialization.MapCodec;

/**
 * Built-in result data and its serialization codec.
 */
public record QuestDetailOutput(QuestOutput quest) implements QuestLookupOutput {
    public static final MapCodec<QuestDetailOutput> CODEC = QuestOutput.CODEC.fieldOf("quest").xmap(QuestDetailOutput::new, QuestDetailOutput::quest);

    @Override
    public DispatchRegistry.Type<QuestDetailOutput> type() {
        return Types.DETAIL;
    }
}
