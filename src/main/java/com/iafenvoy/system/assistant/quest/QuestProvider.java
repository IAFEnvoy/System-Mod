package com.iafenvoy.system.assistant.quest;

import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.data.input.*;
import com.iafenvoy.system.data.output.*;
import com.iafenvoy.system.speech.quest.QuestNarration;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Server-side task integration. Native objects stay in the implementation; only immutable DTOs leave it.
 * IDs and claim references use source().key(localId). Every operation must recheck live player access.
 */
public interface QuestProvider {
    QuestSource source();

    CompletionStage<List<QuestBriefOutput>> query(ExecutionContext context, QuestQueryInput input);

    CompletionStage<List<QuestBriefOutput>> find(ExecutionContext context, QuestFindInput input);

    /**
     * The dispatcher resolves list indices to IDs. A selected summary supplies its native revision;
     * compare it with current scope/definition identity before reading or writing. ID selections have none.
     */
    CompletionStage<QuestOutput> detail(ExecutionContext context, QuestDetailInput input, Optional<String> revision);

    interface Submission extends QuestProvider {
        CompletionStage<QuestSubmitOutput> submit(ExecutionContext context, QuestSubmitInput input, Optional<String> revision);
    }

    interface Rewards extends QuestProvider {
        CompletionStage<QuestClaimOutput> claim(ExecutionContext context, QuestClaimInput input);
    }

    interface Guides extends QuestProvider {
        CompletionStage<QuestGuideOutput> guides(ExecutionContext context, QuestGuideInput input);
    }

    interface StarterGifts extends QuestProvider {
        CompletionStage<StarterGiftOutput> gifts(ExecutionContext context);

        CompletionStage<StarterGiftOutput> claimGifts(ExecutionContext context, StarterGiftInput input);
    }

    interface Tracking extends QuestProvider {
        /**
         * Called on the server thread; never submit tasks, mint reward references or invoke AI here.
         */
        Optional<QuestPinOutput> tracking(ServerPlayer player, String localId);
    }

    interface Narration extends QuestProvider, QuestNarration.Source {
    }
}
