package com.iafenvoy.system.agent.builtin.quest;

import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.builtin.BuiltinModule;
import com.iafenvoy.system.agent.builtin.input.QuestGuideInput;
import com.iafenvoy.system.agent.builtin.output.QuestGuideOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;

import java.util.List;

/**
 * Deterministic local source retrieval; uncertain natural-language intent still uses the normal router.
 */
public final class QuestGuideModule extends BuiltinModule<QuestGuideInput> {
    public QuestGuideModule(ToolRegistry.Tool<QuestGuideInput, QuestGuideOutput> search) {
        super(QuestGuideInput.CODEC, "Search this modpack's local FTB instruction/manual text by topic or question. Sources are explanatory quests with checkmark-only or no objectives; starter gifts are excluded. Returns authorized source passages and originals, not invented instructions. Does not complete quests or claim rewards. Use this for pack guidance, not active quest progress or web research.",
                List.of(search), true, false, ExecutionPolicy.quick(2, 0));
    }

    @Override
    public ModuleRouting<QuestGuideInput> routing() {
        return ModuleRouting.<QuestGuideInput>defaults()
                .command(AgentCommand.argument("<query>", QuestGuideInput::new, "guide", "说明书", "查说明"))
                .semantic(RoutingProfile.read(QuestGuideInput::new,
                        List.of("查一下说明书", "搜索任务里的说明", "查找整合包指南", "说明书里灵力怎么恢复", "在本地指南里查找机器使用方法"),
                        List.of("Search the local manual", "Find instructions in the quest book", "Look up modpack guidance", "How do I restore energy according to the guide?")));
    }

    @Override
    public AgentResult execute(ExecutionContext context, QuestGuideInput input) throws Exception {
        return AgentResult.data("system.result.quest_guide", context.tool(BuiltinTools.QUEST_GUIDE, "guide", input));
    }
}
