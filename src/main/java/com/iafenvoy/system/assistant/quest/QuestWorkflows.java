package com.iafenvoy.system.assistant.quest;

import com.iafenvoy.system.agent.module.AgentModule;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.data.input.*;
import com.iafenvoy.system.data.output.*;
import com.iafenvoy.system.module.ContextTypes;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.builtin.BuiltinTools;
import com.iafenvoy.system.module.builtin.quest.*;

import java.util.List;

/**
 * Installs one provider behind the common workflows, tools and persistent card display protocol.
 */
public final class QuestWorkflows {
    private QuestWorkflows() {
    }

    public static QuestService service(ModuleContext context) {
        return context.sharedRuntime(QuestService.class, () -> new QuestService(context.engine()));
    }

    public static List<AgentModule<?>> install(ModuleContext context, QuestProvider provider) {
        QuestService quests = service(context);
        quests.register(context.id(), provider);
        context.get(ContextTypes.NARRATION).source(quests::narration);
        if (provider instanceof QuestProvider.Tracking tracking)
            context.get(ContextTypes.DISPLAYS).register(context.id(), provider.source().id(),
                    (player, id) -> tracking.tracking(player, id).map(card -> card));
        ToolRegistry.Tool<QuestQueryInput, List<QuestBriefOutput>> query = context.tool(BuiltinTools.QUEST_QUERY,
                () -> new ToolRegistry.Tool<>(BuiltinTools.QUEST_QUERY, false, true, false, quests::query)
                        .forReasoning("List available quest summaries and their sources. Does not submit or claim."));
        ToolRegistry.Tool<QuestFindInput, List<QuestBriefOutput>> find = context.tool(BuiltinTools.QUEST_FIND,
                () -> new ToolRegistry.Tool<>(BuiltinTools.QUEST_FIND, false, true, false, quests::find)
                        .forReasoning("Find visible quests by name or source-qualified ID. Use the returned IDs exactly."));
        ToolRegistry.Tool<QuestDetailInput, QuestOutput> detail = context.tool(BuiltinTools.QUEST_DETAIL,
                () -> new ToolRegistry.Tool<>(BuiltinTools.QUEST_DETAIL, false, true, false, quests::detail)
                        .forReasoning("Read one observed quest ID or displayed list index. Never invent IDs."));
        ToolRegistry.Tool<QuestClaimInput, QuestClaimOutput> claim = context.tool(BuiltinTools.QUEST_CLAIM,
                () -> new ToolRegistry.Tool<>(BuiltinTools.QUEST_CLAIM, true, false, false, quests::claim));
        ToolRegistry.Tool<QuestSubmitInput, QuestSubmitOutput> submit = context.tool(BuiltinTools.QUEST_SUBMIT,
                () -> new ToolRegistry.Tool<>(BuiltinTools.QUEST_SUBMIT, true, false, false, quests::submit));
        ToolRegistry.Tool<QuestGuideInput, QuestGuideOutput> guide = context.tool(BuiltinTools.QUEST_GUIDE,
                () -> new ToolRegistry.Tool<>(BuiltinTools.QUEST_GUIDE, false, true, false, quests::guides)
                        .forReasoning("Search authorized local quest manuals. Does not submit or claim."));
        ToolRegistry.Tool<EmptyInput, StarterGiftOutput> gifts = context.tool(BuiltinTools.STARTER_GIFTS,
                () -> new ToolRegistry.Tool<>(BuiltinTools.STARTER_GIFTS, false, true, false, (execution, _) -> quests.gifts(execution))
                        .forReasoning("Preview available starter gifts without claiming them."));
        ToolRegistry.Tool<StarterGiftInput, StarterGiftOutput> giftClaim = context.tool(BuiltinTools.STARTER_GIFT,
                () -> new ToolRegistry.Tool<>(BuiltinTools.STARTER_GIFT, true, false, false, quests::claimGifts));
        context.engine().tools().registerAll(context.id(), List.of(guide));
        return List.of(new QuestQueryModule(query), new QuestDetailModule(find, detail, gifts), new QuestGuideModule(guide),
                new QuestClaimModule(claim, detail), new QuestSubmitModule(submit), new StarterGiftModule(giftClaim), new StarterGiftQueryModule(gifts));
    }
}
