package com.iafenvoy.system.module.builtin.knowledge;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.data.database.AgentDatabase;
import com.iafenvoy.system.data.database.AgentDatabases;
import com.iafenvoy.system.data.database.DatabaseTable;
import com.iafenvoy.system.data.database.KnowledgeIndex;
import com.iafenvoy.system.data.input.DatabaseQueryInput;
import com.iafenvoy.system.data.input.QuestGuideInput;
import com.iafenvoy.system.data.output.DatabaseQueryOutput;
import com.iafenvoy.system.data.output.QuestGuideOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;
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
        AgentDatabases databases) implements ToolRegistry.Handler<DatabaseQueryInput, DatabaseQueryOutput> {
    public static final DatabaseTable<List<KnowledgeIndex.Document>> KNOWLEDGE = AgentDatabase.documents(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "knowledge"));

    public ToolRegistry.Tool<DatabaseQueryInput, DatabaseQueryOutput> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.DATABASE_QUERY, false, true, false, this);
    }

    @Override
    public CompletionStage<DatabaseQueryOutput> execute(ExecutionContext context, DatabaseQueryInput input) {
        try {
            context.ensureMemoryCurrent();
            List<DatabaseQueryOutput.Source> sources = new ArrayList<>();
            this.query(this.databases.player(context.player()), "player", context, input, sources);
            this.query(this.databases.shared(), "shared", context, input, sources);
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
