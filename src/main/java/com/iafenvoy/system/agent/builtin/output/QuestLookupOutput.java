package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.util.codec.DispatchRegistry;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;

/**
 * Built-in result data and its serialization codec.
 */
public interface QuestLookupOutput {
    Codec<QuestLookupOutput> CODEC = Types.REGISTRY.codec();

    DispatchRegistry.Type<? extends QuestLookupOutput> type();

    final class Types {
        public static final DispatchRegistry<QuestLookupOutput> REGISTRY = new DispatchRegistry<>("type", QuestLookupOutput::type);
        public static final DispatchRegistry.Type<QuestPageOutput> PAGE = REGISTRY.register(
                Identifier.fromNamespaceAndPath("system", "quest_page"), QuestPageOutput.CODEC);
        public static final DispatchRegistry.Type<QuestDetailOutput> DETAIL = REGISTRY.register(
                Identifier.fromNamespaceAndPath("system", "quest_detail"), QuestDetailOutput.CODEC);
        public static final DispatchRegistry.Type<StarterGiftOutput> STARTER_GIFT = REGISTRY.register(
                Identifier.fromNamespaceAndPath("system", "starter_gift"), StarterGiftOutput.CODEC);

        private Types() {
        }
    }
}
