package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.input.QuestGuideInput;
import com.iafenvoy.system.agent.builtin.output.QuestGuideOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.database.KnowledgeIndex;
import com.iafenvoy.system.util.LegacyText;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.CompletionStage;

/**
 * Snapshot and authorize on the server thread, rank immutable text off-thread, then revalidate every source.
 */
final class FtbQuestGuideTool implements ToolRegistry.Handler<QuestGuideInput, QuestGuideOutput> {
    private final FtbQuestAccess access;

    FtbQuestGuideTool(FtbQuestAccess access) {
        this.access = access;
    }

    ToolRegistry.Tool<QuestGuideInput, QuestGuideOutput> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.QUEST_GUIDE, false, true, false, this);
    }

    private record Snapshot(FtbQuestKnowledge.Snapshot knowledge, UUID team, Set<String> allowed) {
    }

    @Override
    public CompletionStage<QuestGuideOutput> execute(ExecutionContext context, QuestGuideInput input) {
        return this.access.game.onPlayer(context, this::snapshot).thenCompose(snapshot ->
                this.access.knowledge.search(context, snapshot.knowledge(), snapshot.allowed(), input.query(), 5)
                        .thenCompose(hits -> this.access.game.onPlayer(context, player -> this.result(player, input, snapshot, hits))));
    }

    private QuestGuideOutput result(ServerPlayer player, QuestGuideInput input, Snapshot snapshot, List<KnowledgeIndex.Hit> hits) {
        if (!ServerQuestFile.exists()) throw new AgentException("CAPABILITY_UNAVAILABLE");
        ServerQuestFile file = ServerQuestFile.getInstance();
        TeamData team = file.getTeamData(player).orElseThrow(() -> new AgentException("CAPABILITY_UNAVAILABLE"));
        if (file != snapshot.knowledge().file() || !this.access.knowledge.current(snapshot.knowledge()) || !team.getTeamId().equals(snapshot.team())
                || !player.clientInformation().language().equals(snapshot.knowledge().language()))
            throw new AgentException("CANCELLED");
        List<QuestGuideOutput.Entry> entries = new ArrayList<>();
        for (KnowledgeIndex.Hit hit : hits) {
            KnowledgeIndex.Document document = hit.document();
            Quest quest = file.getQuest(Long.parseUnsignedLong(document.id(), 16));
            if (quest == null || !FtbQuestReader.guideReadable(team, quest)) continue;
            String body = FtbQuestText.description(player, team, quest);
            if (LegacyText.plain(body).isBlank() || !FtbQuestKnowledge.document(this.access.game.server(), snapshot.knowledge().language(), quest).equals(document))
                continue;
            entries.add(new QuestGuideOutput.Entry(document.id(), FtbQuestText.title(player, quest), FtbQuestText.title(player, quest.getChapter()),
                    hit.passage(), body));
        }
        return new QuestGuideOutput(input.query(), entries);
    }

    private Snapshot snapshot(ServerPlayer player) {
        if (!ServerQuestFile.exists()) throw new AgentException("CAPABILITY_UNAVAILABLE");
        ServerQuestFile file = ServerQuestFile.getInstance();
        TeamData team = file.getTeamData(player).orElseThrow(() -> new AgentException("CAPABILITY_UNAVAILABLE"));
        FtbQuestKnowledge.Snapshot knowledge = this.access.knowledge.snapshot(file, player.clientInformation().language());
        Set<String> allowed = new HashSet<>();
        for (KnowledgeIndex.Document document : knowledge.documents()) {
            Quest quest = file.getQuest(Long.parseUnsignedLong(document.id(), 16));
            if (quest != null && FtbQuestReader.guideReadable(team, quest)
                    && (!quest.getHideTextUntilComplete().get(quest.getChapter().isHideTextUntilComplete()) || team.isCompleted(quest)))
                allowed.add(document.id());
        }
        return new Snapshot(knowledge, team.getTeamId(), Set.copyOf(allowed));
    }
}
