package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.builtin.input.QuestDetailInput;
import com.iafenvoy.system.network.ServerNetworkHandler;
import com.iafenvoy.system.runtime.server.AssistantServer;
import com.iafenvoy.system.util.Json;
import dev.ftb.mods.ftbquests.api.event.progress.ProgressEventData;
import dev.ftb.mods.ftbquests.api.event.progress.ProgressType;
import dev.ftb.mods.ftbquests.api.event.progress.QuestProgressEvent;
import dev.ftb.mods.ftbquests.api.neoforge.FTBQuestsEvent;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Optional, server-thread FTB event bridge. Progress changes produce system messages without inference or rewards.
 */
final class FtbQuestNotifications {
    private static final Map<MinecraftServer, FtbQuestNotifications> SERVERS = new IdentityHashMap<>();
    private static final int MAX_COMPLETIONS_PER_TICK = 256;
    private static boolean installed;
    private final ServerQuestFile file;
    private final Map<UUID, TeamState> teams = new HashMap<>();
    private int ticks;

    private FtbQuestNotifications(ServerQuestFile file) {
        this.file = file;
    }

    static void install() {
        if (installed) return;
        installed = true;
        NeoForge.EVENT_BUS.addListener(FTBQuestsEvent.QuestProgress.class, event -> {
            QuestProgressEvent.Data data = event.getEventData();
            FtbQuestNotifications tracker = changed(data.progressData());
            if (tracker != null && data.type() == ProgressType.COMPLETED) tracker.completed(data.progressData());
        });
        NeoForge.EVENT_BUS.addListener(FTBQuestsEvent.TaskProgress.class, event -> changed(event.getEventData().progressData()));
        NeoForge.EVENT_BUS.addListener(FTBQuestsEvent.ChapterProgress.class, event -> changed(event.getEventData().progressData()));
        NeoForge.EVENT_BUS.addListener(FTBQuestsEvent.FileProgress.class, event -> changed(event.getEventData().progressData()));
        NeoForge.EVENT_BUS.addListener(FTBQuestsEvent.ClearFileCache.class, event -> {
            if (event.getEventData().file() instanceof ServerQuestFile file) SERVERS.remove(file.server);
        });
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, FtbQuestNotifications::tick);
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, event -> SERVERS.remove(event.getServer()));
    }

    private static FtbQuestNotifications changed(ProgressEventData<?> data) {
        // Client-side progress echoes use the same event types and must never enter this bridge.
        if (!(data.object().getQuestFile() instanceof ServerQuestFile file)) return null;
        FtbQuestNotifications tracker = SERVERS.get(file.server);
        if (tracker == null || tracker.file != file) return null;
        TeamState state = tracker.teams.get(data.teamData().getTeamId());
        if (state == null || state.team != data.teamData()) return null;
        state.dirty = true;
        if (!QuestObjectBase.shouldSendNotifications()) state.silent = true;
        return tracker;
    }

    private void completed(ProgressEventData<Quest> data) {
        TeamState state = this.teams.get(data.teamData().getTeamId());
        Quest quest = data.object();
        if (state == null || state.silent || state.team.isLocked() || !visible(state.team, quest)) return;
        AssistantServer runtime = AssistantServer.get(this.file.server);
        if (runtime == null) return;
        List<Recipient> recipients = new ArrayList<>();
        for (ServerPlayer player : data.onlineMembers()) {
            Recipient previous = state.recipients.get(player.getUUID());
            if (previous != null && previous.valid(runtime, this.file, state.team.getTeamId())) recipients.add(previous);
        }
        if (recipients.isEmpty() || state.completed.size() >= MAX_COMPLETIONS_PER_TICK) return;
        state.completed.putIfAbsent(quest.getId(), new Completion(notification(quest, true), List.copyOf(recipients)));
    }

    private static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        AssistantServer runtime = AssistantServer.get(server);
        if (runtime == null || !ServerQuestFile.exists()) {
            SERVERS.remove(server);
            return;
        }
        ServerQuestFile file = ServerQuestFile.getInstance();
        if (file.server != server) return;
        FtbQuestNotifications tracker = SERVERS.get(server);
        if (tracker == null || tracker.file != file) {
            tracker = new FtbQuestNotifications(file);
            SERVERS.put(server, tracker);
        }
        tracker.flush(runtime);
    }

    private void flush(AssistantServer runtime) {
        this.ticks++;
        Map<UUID, TeamData> onlineTeams = new LinkedHashMap<>();
        Map<UUID, Map<UUID, Recipient>> recipients = new HashMap<>();
        for (ServerPlayer player : this.file.server.getPlayerList().getPlayers()) {
            if (!runtime.notificationsEnabled(player)) continue;
            this.file.getTeamData(player).ifPresent(team -> {
                onlineTeams.put(team.getTeamId(), team);
                recipients.computeIfAbsent(team.getTeamId(), ignored -> new LinkedHashMap<>()).put(player.getUUID(),
                        new Recipient(player, ServerNetworkHandler.connectionId(player), runtime.engine().incarnation(player.getUUID())));
            });
        }
        this.teams.keySet().retainAll(onlineTeams.keySet());
        for (Map.Entry<UUID, TeamData> entry : onlineTeams.entrySet()) {
            UUID id = entry.getKey();
            TeamData team = entry.getValue();
            Map<UUID, Recipient> current = recipients.get(id);
            TeamState state = this.teams.get(id);
            if (state == null || state.team != team) {
                // Login, first binding and team replacement establish a silent baseline, never a backlog.
                state = new TeamState(team, this.available(team), Map.copyOf(current));
                this.teams.put(id, state);
                continue;
            }
            // Events coalesce within a tick; the slow refresh also observes locks, resets and repeat cooldowns.
            if (state.dirty || !state.recipients.equals(current) || this.ticks % 20 == 0) {
                Map<Long, Quest> available = this.available(team);
                for (Completion completion : state.completed.values())
                    for (Recipient recipient : completion.recipients())
                        if (recipient.valid(runtime, this.file, id)) runtime.notifyEvent(recipient.player(), completion.result());
                if (!state.silent && state.locked == team.isLocked())
                    for (Map.Entry<Long, Quest> quest : available.entrySet()) {
                        if (state.available.contains(quest.getKey()) || team.isCompleted(quest.getValue())) continue;
                        AgentResult result = notification(quest.getValue(), false);
                        for (Recipient recipient : current.values()) {
                            if (!recipient.equals(state.recipients.get(recipient.player().getUUID()))) continue;
                            if (recipient.valid(runtime, this.file, id)) runtime.notifyEvent(recipient.player(), result);
                        }
                    }
                state.available = Set.copyOf(available.keySet());
                state.locked = team.isLocked();
                state.completed.clear();
                state.dirty = false;
                state.silent = false;
            }
            state.recipients = Map.copyOf(current);
        }
    }

    private Map<Long, Quest> available(TeamData team) {
        Map<Long, Quest> result = new LinkedHashMap<>();
        if (team.isLocked()) return result;
        for (Chapter chapter : this.file.getAllChapters()) {
            if (chapter.isAlwaysInvisible()) continue;
            for (Quest quest : chapter.getQuests())
                if (quest.isVisible(team) && team.canStartTasks(quest)) result.put(quest.getId(), quest);
        }
        // Keep completed but startable quests in the baseline so a reset is not itself a new unlock.
        return result;
    }

    private static boolean visible(TeamData team, Quest quest) {
        return !quest.getChapter().isAlwaysInvisible() && quest.isVisible(team);
    }

    private static AgentResult notification(Quest quest, boolean completed) {
        String key = completed ? "system.result.quest_completed" : "system.result.quest_unlocked";
        String id = String.format(Locale.ROOT, "%016x", quest.getId());
        return new AgentResult("SUCCEEDED", key, "", List.of(new AgentResult.Card(key,
                ResultTypes.QUEST_NOTICE.of(new ResultTypes.QuestNotice(Json.clip(quest.getTitle().getString(), 256),
                        Json.clip(quest.getChapter().getTitle().getString(), 128))), List.of(
                new AgentResult.Action("system.action.quest_detail", ResultTypes.QUEST_ACTION, QuestDetailInput.byId(id))))));
    }

    private record Recipient(ServerPlayer player, UUID connection, long incarnation) {
        boolean valid(AssistantServer runtime, ServerQuestFile file, UUID team) {
            return runtime.notificationsEnabled(this.player) && runtime.engine().current(this.player.getUUID(), this.incarnation)
                    && this.connection.equals(ServerNetworkHandler.connectionId(this.player))
                    && file.getTeamData(this.player).map(data -> data.getTeamId().equals(team)).orElse(false);
        }
    }

    private record Completion(AgentResult result, List<Recipient> recipients) {
    }

    private static final class TeamState {
        private final TeamData team;
        private Set<Long> available;
        private Map<UUID, Recipient> recipients;
        private final Map<Long, Completion> completed = new LinkedHashMap<>();
        private boolean locked;
        private boolean dirty;
        private boolean silent;

        private TeamState(TeamData team, Map<Long, Quest> available, Map<UUID, Recipient> recipients) {
            this.team = team;
            this.available = Set.copyOf(available.keySet());
            this.recipients = recipients;
            this.locked = team.isLocked();
        }
    }
}
