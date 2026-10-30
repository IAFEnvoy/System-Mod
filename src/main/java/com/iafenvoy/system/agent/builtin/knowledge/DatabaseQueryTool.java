package com.iafenvoy.system.agent.builtin.knowledge;

import com.iafenvoy.system.agent.AgentEngine;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.input.DatabaseQueryInput;
import com.iafenvoy.system.agent.builtin.input.QuestGuideInput;
import com.iafenvoy.system.agent.builtin.output.DatabaseQueryOutput;
import com.iafenvoy.system.agent.builtin.output.QuestGuideOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.database.AgentDatabase;
import com.iafenvoy.system.agent.database.DatabaseTable;
import com.iafenvoy.system.agent.database.KnowledgeIndex;
import com.iafenvoy.system.util.LegacyText;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * The public knowledge corpus and current player's private corpus share the same local query operation.
 */
public record DatabaseQueryTool(
        AgentEngine engine) implements ToolRegistry.Handler<DatabaseQueryInput, DatabaseQueryOutput> {
    public static final DatabaseTable<List<KnowledgeIndex.Document>> KNOWLEDGE = AgentDatabase.documents(Identifier.parse("system:knowledge"));

    public ToolRegistry.Tool<DatabaseQueryInput, DatabaseQueryOutput> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.DATABASE_QUERY, false, true, false, this);
    }

    @Override
    public CompletionStage<DatabaseQueryOutput> execute(ExecutionContext context, DatabaseQueryInput input) {
        try {
            context.ensureMemoryCurrent();
            List<DatabaseQueryOutput.Source> sources = new ArrayList<>();
            this.query(this.engine.databases().player(context.player()), "player", context, input, sources);
            this.query(this.engine.databases().shared(), "shared", context, input, sources);
            // FTB's shared raw corpus is never scanned directly: its exported tool checks native visibility.
            if (context.toolAvailable(BuiltinTools.QUEST_GUIDE)) {
                QuestGuideOutput guides = context.tool(BuiltinTools.QUEST_GUIDE, "knowledge/ftb", new QuestGuideInput(input.query()));
                for (QuestGuideOutput.Entry entry : guides.entries())
                    sources.add(new DatabaseQueryOutput.Source("ftb/" + entry.id(),
                            LegacyText.plain(entry.title().getString()), LegacyText.plain(entry.chapter().getString()), entry.passage()));
            }
            context.ensureMemoryCurrent();
            return CompletableFuture.completedFuture(new DatabaseQueryOutput(sources));
        } catch (Exception error) {
            return CompletableFuture.failedFuture(error);
        }
    }

    private void query(AgentDatabase database, String scope, ExecutionContext context, DatabaseQueryInput input,
                       List<DatabaseQueryOutput.Source> sources) {
        List<KnowledgeIndex.Document> documents = database.get(KNOWLEDGE, context.language()).orElse(List.of());
        for (KnowledgeIndex.Hit hit : database.search(KNOWLEDGE.id(), context.language(),
                documents.stream().map(KnowledgeIndex.Document::id).collect(Collectors.toSet()), input.query(), 5))
            sources.add(new DatabaseQueryOutput.Source(scope + "/" + hit.document().id(), hit.document().title(), hit.document().chapter(), hit.passage()));
    }
}
