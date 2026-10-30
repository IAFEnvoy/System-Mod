package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.agent.AgentEngine;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.builtin.input.QuestDetailInput;
import com.iafenvoy.system.agent.builtin.output.QuestStartupOutput;
import com.iafenvoy.system.agent.builtin.output.QuestSubmission;
import com.iafenvoy.system.agent.builtin.output.QuestUnlockedOutput;
import com.iafenvoy.system.agent.builtin.quest.StarterGiftModule;
import com.iafenvoy.system.assistant.AssistantServer;
import com.iafenvoy.system.card.CardUpdate;
import com.iafenvoy.system.module.builtin.QuestSystemModule;
import com.iafenvoy.system.network.ServerNetworkHandler;
import com.iafenvoy.system.util.LegacyText;
import dev.ftb.mods.ftbquests.api.event.progress.ProgressEventData;
import dev.ftb.mods.ftbquests.api.event.progress.ProgressType;
import dev.ftb.mods.ftbquests.api.event.progress.QuestProgressEvent;
import dev.ftb.mods.ftbquests.api.neoforge.FTBQuestsEvent;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.reward.ChoiceReward;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardAutoClaim;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import java.util.*;
import java.util.concurrent.CompletionStage;

/**
 * Optional, server-thread FTB event bridge. Notices expose trusted actions without granting rewards on arrival.
 */
final class FtbQuestNotifications {
    private static final Map<MinecraftServer, FtbQuestNotifications> SERVERS = new IdentityHashMap<>();
    private static final Map<MinecraftServer, FtbQuestAccess> ACCESS = new IdentityHashMap<>();
    private static final int MAX_COMPLETIONS_PER_TICK = 256;
    private static boolean installed;
    private ServerQuestFile file;
    private final FtbQuestAccess access;
    private final Map<UUID, TeamState> teams = new HashMap<>();
    private int ticks;

    private FtbQuestNotifications(ServerQuestFile file, FtbQuestAccess access) {
        this.file = file;
        this.access = access;
    }

    static FtbQuestKnowledge knowledge(AgentEngine engine, MinecraftServer server) {
        FtbQuestAccess access = ACCESS.get(server);
        return access == null ? new FtbQuestKnowledge(engine, server) : access.knowledge;
    }

    static void install(MinecraftServer server, FtbQuestAccess access) {
        ACCESS.put(server, access);
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
            if (event.getEventData().file() instanceof ServerQuestFile file) {
                FtbQuestNotifications tracker = SERVERS.get(file.server);
                if (tracker != null) tracker.teams.clear();
                FtbQuestAccess current = ACCESS.get(file.server);
                if (current != null) current.knowledge.invalidate();
            }
        });
    }

    static void close(MinecraftServer server) {
        SERVERS.remove(server);
        ACCESS.remove(server);
    }

    static CompletionStage<Integer> reloadKnowledge(MinecraftServer server) {
        FtbQuestAccess access = ACCESS.get(server);
        if (access == null) throw new AgentException("CAPABILITY_UNAVAILABLE");
        return access.knowledge.reload();
    }

    static void remove(ServerPlayer player) {
        FtbQuestAccess access = ACCESS.get(player.level().getServer());
        if (access != null) access.clear(player.getUUID());
        FtbQuestNotifications tracker = SERVERS.get(player.level().getServer());
        if (tracker == null) return;
        for (TeamState state : tracker.teams.values()) {
            Map<UUID, Recipient> recipients = new HashMap<>(state.recipients);
            recipients.remove(player.getUUID());
            state.recipients = Map.copyOf(recipients);
            state.completed.replaceAll((_, completion) -> new Completion(completion.quest(),
                    completion.recipients().stream().filter(recipient -> recipient.player() != player).toList()));
        }
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
        if (state == null || state.silent || state.team.isLocked() || !visible(state.team, quest)
                || FtbQuestReader.kind(quest) != FtbQuestReader.Kind.NORMAL) return;
        AssistantServer runtime = AssistantServer.get(this.file.server);
        if (runtime == null) return;
        List<Recipient> recipients = new ArrayList<>();
        for (ServerPlayer player : data.onlineMembers()) {
            Recipient previous = state.recipients.get(player.getUUID());
            if (previous != null && previous.valid(runtime, this.file, state.team.getTeamId()))
                recipients.add(previous);
        }
        if (recipients.isEmpty() || state.completed.size() >= MAX_COMPLETIONS_PER_TICK) return;
        state.completed.putIfAbsent(quest.getId(), new Completion(quest, List.copyOf(recipients)));
    }

    static void tick(MinecraftServer server) {
        AssistantServer runtime = AssistantServer.get(server);
        if (runtime == null) {
            SERVERS.remove(server);
            return;
        }
        if (!ServerQuestFile.exists()) return;
        ServerQuestFile file = ServerQuestFile.getInstance();
        if (file.server != server) return;
        FtbQuestAccess access = ACCESS.get(server);
        if (access == null) return;
        FtbQuestNotifications tracker = SERVERS.get(server);
        if (tracker == null || tracker.access != access) {
            tracker = new FtbQuestNotifications(file, access);
            SERVERS.put(server, tracker);
        } else if (tracker.file != file) {
            tracker.file = file;
            tracker.teams.clear();
        }
        tracker.flush(runtime);
    }

    private void flush(AssistantServer runtime) {
        this.ticks++;
        Map<UUID, TeamData> onlineTeams = new LinkedHashMap<>();
        Map<UUID, Map<UUID, Recipient>> recipients = new HashMap<>();
        for (ServerPlayer player : this.file.server.getPlayerList().getPlayers()) {
            if (!runtime.players().notificationsEnabled(player) || !runtime.moduleManager().hasType(player.getUUID(), QuestSystemModule.CODEC))
                continue;
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
                // Restored sessions and team changes establish a silent baseline for existing quests.
                state = new TeamState(team, this.available(team), Map.copyOf(current));
                this.teams.put(id, state);
            }
            // Events coalesce within a tick; the slow refresh also observes locks, resets and repeat cooldowns.
            if (state.dirty || !state.recipients.equals(current) || this.ticks % 20 == 0) {
                Map<Long, Quest> available = this.available(team);
                for (Completion completion : state.completed.values())
                    for (Recipient recipient : completion.recipients())
                        if (recipient.valid(runtime, this.file, id))
                            runtime.players().notifyEvent(recipient.player(), this.completionNotification(completion.quest(), recipient.player(), team));
                if (!state.silent && state.locked == team.isLocked()) {
                    Set<Long> previous = state.available;
                    List<Quest> newlyAvailable = available.entrySet().stream()
                            .filter(quest -> !previous.contains(quest.getKey()) && !team.isCompleted(quest.getValue()))
                            .map(Map.Entry::getValue).toList();
                    List<Quest> unlocked = newlyAvailable.stream().filter(quest -> FtbQuestReader.kind(quest) == FtbQuestReader.Kind.NORMAL).toList();
                    // Each completion keeps its own notice; unlock batches use details for one quest and a list for several.
                    for (Recipient recipient : current.values()) {
                        if (!recipient.equals(state.recipients.get(recipient.player().getUUID())) || !recipient.valid(runtime, this.file, id))
                            continue;
                        ServerPlayer player = recipient.player();
                        if (newlyAvailable.stream().anyMatch(quest -> FtbQuestReader.starterGiftAvailable(player, team, quest)))
                            runtime.players().notifyEvent(player, StarterGiftModule.preview(this.access.gifts.preview(player, this.file, team)));
                        if (!unlocked.isEmpty())
                            runtime.players().notifyEvent(player, this.unlockNotification(unlocked, player, team));
                    }
                }
                state.available = Set.copyOf(available.keySet());
                state.locked = team.isLocked();
                state.completed.clear();
                state.dirty = false;
                state.silent = false;
            }
            state.recipients = Map.copyOf(current);
            for (Recipient recipient : current.values()) {
                if (!recipient.valid(runtime, this.file, id) || !runtime.players().consumeBindingIntroduction(recipient.player()))
                    continue;
                this.startup(runtime, recipient.player(), team);
            }
        }
    }

    private Map<Long, Quest> available(TeamData team) {
        Map<Long, Quest> result = new LinkedHashMap<>();
        for (Quest quest : new FtbQuestReader(this.file, team).notificationBaseline()) result.put(quest.getId(), quest);
        return result;
    }

    private static boolean visible(TeamData team, Quest quest) {
        return FtbQuestReader.readable(team, quest);
    }

    private void startup(AssistantServer runtime, ServerPlayer player, TeamData team) {
        FtbQuestReader reader = new FtbQuestReader(this.file, team);
        // Binding was sent before initialization; enqueue all available gifts before the quest overview.
        if (!reader.availableStarterGifts(player).isEmpty()) runtime.players().notifyEvent(player,
                StarterGiftModule.preview(this.access.gifts.preview(player, this.file, team)));
        List<Quest> active = reader.active(player).stream().filter(quest -> FtbQuestReader.kind(quest) == FtbQuestReader.Kind.NORMAL).toList();
        if (!active.isEmpty()) {
            String key = "system.result.quest_startup";
            // Keep the introduction brief; viewing all always runs a fresh normal query.
            QuestStartupOutput overview = new QuestStartupOutput(active.size(), active.stream().limit(3)
                    .map(quest -> FtbQuestAccess.brief(player, team, quest, 0)).toList());
            runtime.players().notifyEvent(player, new AgentResult(AgentResult.Status.SUCCEEDED, key, "", List.of(CardUpdate.show(overview))));
        }
    }

    private AgentResult completionNotification(Quest quest, ServerPlayer player, TeamData team) {
        String key = "system.result.quest_completed";
        String id = String.format(Locale.ROOT, "%016x", quest.getId());
        List<Reward> claimable = quest.getRewards().stream().filter(reward -> FtbQuestReader.canClaim(player, team, reward)).toList();
        Optional<String> claimRef = claimable.size() == 1 && !(claimable.getFirst() instanceof ChoiceReward)
                ? FtbQuestAccess.reference(player, team, quest, claimable.getFirst(), this.access.references) : Optional.empty();
        return AgentResult.data(key, new ResultTypes.QuestNotice(id, FtbQuestText.title(player, quest), FtbQuestText.title(player, quest.getChapter()),
                quest.getRewards().stream().filter(reward -> !team.isRewardBlocked(reward) && reward.getAutoClaimType() != RewardAutoClaim.INVISIBLE)
                        .flatMap(reward -> FtbQuestAccess.previewItems(reward).stream()).toList(),
                "", List.of(),
                quest.getRewards().stream().filter(reward -> !team.isRewardBlocked(reward) && reward.getAutoClaimType() != RewardAutoClaim.INVISIBLE
                        && FtbQuestAccess.previewItems(reward).isEmpty()).flatMap(reward -> FtbQuestPreviews.reward(player, team, reward).stream()).toList(),
                List.of(), false, ResultTypes.QuestEvent.COMPLETED,
                QuestSubmission.NONE, !claimable.isEmpty(), claimRef,
                FtbQuestSpeech.content(player, team, quest, "completed")));
    }

    private AgentResult unlockNotification(List<Quest> quests, ServerPlayer player, TeamData team) {
        if (quests.size() == 1) {
            Quest quest = quests.getFirst();
            return AgentResult.data("system.result.quest_unlocked", FtbQuestAccess.detail(player, team, quest,
                    QuestDetailInput.byId(FtbQuestAccess.hex(quest.getId())), this.access.references, "unlocked"));
        }
        return AgentResult.data("system.result.quest_unlocked", new QuestUnlockedOutput(
                quests.stream().map(quest -> FtbQuestAccess.brief(player, team, quest, 0)).toList()));
    }

    // The native event and explicit submission converge on the same pending completion entry.
    static void submitted(ServerPlayer player, ServerQuestFile file, TeamData team, Quest quest) {
        if (FtbQuestReader.kind(quest) != FtbQuestReader.Kind.NORMAL) return;
        AssistantServer runtime = AssistantServer.get(file.server);
        FtbQuestAccess access = ACCESS.get(file.server);
        if (runtime == null || access == null || !runtime.players().notificationsEnabled(player)
                || !runtime.moduleManager().hasType(player.getUUID(), QuestSystemModule.CODEC)) return;
        FtbQuestNotifications tracker = SERVERS.computeIfAbsent(file.server, _ -> new FtbQuestNotifications(file, access));
        TeamState state = tracker.teams.computeIfAbsent(team.getTeamId(), _ -> new TeamState(team, tracker.available(team), Map.of()));
        Completion previous = state.completed.get(quest.getId());
        List<Recipient> recipients = new ArrayList<>(previous == null ? List.of() : previous.recipients());
        if (recipients.stream().noneMatch(recipient -> recipient.player() == player))
            recipients.add(new Recipient(player, ServerNetworkHandler.connectionId(player), runtime.engine().incarnation(player.getUUID())));
        state.completed.put(quest.getId(), new Completion(quest, List.copyOf(recipients)));
        state.dirty = true;
    }

    private record Recipient(ServerPlayer player, UUID connection, long incarnation) {
        boolean valid(AssistantServer runtime, ServerQuestFile file, UUID team) {
            return runtime.players().notificationsEnabled(this.player) && runtime.moduleManager().hasType(this.player.getUUID(), QuestSystemModule.CODEC)
                    && runtime.engine().current(this.player.getUUID(), this.incarnation)
                    && this.connection.equals(ServerNetworkHandler.connectionId(this.player))
                    && file.getTeamData(this.player).map(data -> data.getTeamId().equals(team)).orElse(false);
        }
    }

    private record Completion(Quest quest, List<Recipient> recipients) {
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
