package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentEngine;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.database.AgentDatabase;
import com.iafenvoy.system.agent.database.KnowledgeIndex;
import com.iafenvoy.system.util.LegacyText;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Static localized FTB documents are cached on the server thread; persistence and indexing run on workers.
 */
final class FtbQuestKnowledge {
    private static final Identifier SOURCE = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "ftb_guides");
    private final AgentEngine engine;
    private final MinecraftServer server;
    private final AgentDatabase database;
    private final Map<String, Snapshot> snapshots = new HashMap<>();
    private final Map<String, Snapshot> indexed = new HashMap<>();
    private final AtomicLong generation = new AtomicLong();
    private final AtomicBoolean reloading = new AtomicBoolean();
    private final Object updates = new Object();

    FtbQuestKnowledge(AgentEngine engine, MinecraftServer server) {
        this.engine = engine;
        this.server = server;
        this.database = engine.databases().shared();
    }

    record Snapshot(ServerQuestFile file, long generation, String language, List<KnowledgeIndex.Document> documents) {
    }

    Snapshot snapshot(ServerQuestFile file, String language) {
        Snapshot cached = this.snapshots.get(language);
        if (cached != null && cached.file() == file) return cached;
        List<KnowledgeIndex.Document> documents = FtbQuestReader.guideCandidates(file).stream()
                .map(quest -> document(this.server, language, quest)).filter(document -> !document.body().isBlank()).toList();
        Snapshot snapshot = new Snapshot(file, this.generation.get(), language, documents);
        this.snapshots.put(language, snapshot);
        return snapshot;
    }

    static KnowledgeIndex.Document document(MinecraftServer server, String language, Quest quest) {
        return new KnowledgeIndex.Document(String.format(Locale.ROOT, "%016x", quest.getId()),
                LegacyText.plain(FtbQuestText.title(server.registryAccess(), language, quest).getString()),
                LegacyText.plain(FtbQuestText.title(server.registryAccess(), language, quest.getChapter()).getString()),
                LegacyText.plain(FtbQuestText.rawDescription(server.registryAccess(), language, quest)));
    }

    boolean current(Snapshot snapshot) {
        return snapshot.generation() == this.generation.get();
    }

    CompletionStage<List<KnowledgeIndex.Hit>> search(ExecutionContext context, Snapshot snapshot, Set<String> allowed, String query, int limit) {
        return this.engine.maintenance(() -> {
            context.check();
            synchronized (this.updates) {
                if (!this.current(snapshot)) throw new AgentException("CANCELLED");
                if (this.indexed.get(snapshot.language()) != snapshot) {
                    this.database.put(AgentDatabase.documents(SOURCE), snapshot.language(), snapshot.documents());
                    this.indexed.put(snapshot.language(), snapshot);
                }
            }
            return this.database.search(SOURCE, snapshot.language(), allowed, query, limit);
        });
    }

    // Invalidating never waits on file I/O; late queries cannot publish results from the previous generation.
    void invalidate() {
        this.generation.incrementAndGet();
        this.snapshots.clear();
    }

    CompletionStage<Integer> reload() {
        if (!this.reloading.compareAndSet(false, true)) throw new AgentException("BUSY");
        try {
            if (!ServerQuestFile.exists()) throw new AgentException("CAPABILITY_UNAVAILABLE");
            ServerQuestFile file = ServerQuestFile.getInstance();
            Set<String> languages = new TreeSet<>(this.snapshots.keySet());
            languages.add(file.getFallbackLocale());
            this.server.getPlayerList().getPlayers().forEach(player -> languages.add(player.clientInformation().language()));
            this.invalidate();
            List<Snapshot> snapshots = languages.stream().map(language -> this.snapshot(file, language)).toList();
            return this.engine.maintenance(() -> {
                synchronized (this.updates) {
                    if (snapshots.stream().anyMatch(snapshot -> !this.current(snapshot)))
                        throw new AgentException("CANCELLED");
                    Map<String, List<KnowledgeIndex.Document>> documents = new LinkedHashMap<>();
                    snapshots.forEach(snapshot -> documents.put(snapshot.language(), snapshot.documents()));
                    this.database.replace(AgentDatabase.documents(SOURCE), documents);
                    languages.forEach(language -> this.database.prepareIndex(SOURCE, language));
                    this.indexed.clear();
                    snapshots.forEach(snapshot -> this.indexed.put(snapshot.language(), snapshot));
                    if (snapshots.stream().anyMatch(snapshot -> !this.current(snapshot)))
                        throw new AgentException("CANCELLED");
                    return (int) snapshots.stream().flatMap(snapshot -> snapshot.documents().stream()).map(KnowledgeIndex.Document::id).distinct().count();
                }
            }).whenComplete((unused1, unused2) -> this.reloading.set(false));
        } catch (RuntimeException error) {
            this.reloading.set(false);
            throw error;
        }
    }
}
