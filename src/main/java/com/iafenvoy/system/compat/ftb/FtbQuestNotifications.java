package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.builtin.input.QuestClaimInput;
import com.iafenvoy.system.agent.builtin.input.QuestDetailInput;
import com.iafenvoy.system.agent.builtin.input.QuestQueryInput;
import com.iafenvoy.system.agent.builtin.output.QuestStartupOutput;
import com.iafenvoy.system.agent.builtin.quest.QuestSubmitModule;
import com.iafenvoy.system.agent.builtin.quest.StarterGiftModule;
import com.iafenvoy.system.assistant.AssistantServer;
import com.iafenvoy.system.network.ServerNetworkHandler;
import com.iafenvoy.system.util.MiscUtil;
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
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

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
            }
        });
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, FtbQuestNotifications::tick);
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, event -> {
            SERVERS.remove(event.getServer());
            ACCESS.remove(event.getServer());
        });
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

    private static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
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
            if (!runtime.players().notificationsEnabled(player)) continue;
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
                Set<UUID> giftRecipients = new HashSet<>();
                for (Completion completion : state.completed.values())
                    for (Recipient recipient : completion.recipients())
                        if (recipient.valid(runtime, this.file, id))
                            runtime.players().notifyEvent(recipient.player(), this.notification(completion.quest(), true, recipient.player(), team));
                if (!state.silent && state.locked == team.isLocked())
                    for (Map.Entry<Long, Quest> quest : available.entrySet()) {
                        if (state.available.contains(quest.getKey()) || team.isCompleted(quest.getValue())) continue;
                        for (Recipient recipient : current.values()) {
                            if (!recipient.equals(state.recipients.get(recipient.player().getUUID()))) continue;
                            if (recipient.valid(runtime, this.file, id)) {
                                if (FtbQuestReader.kind(quest.getValue()) == FtbQuestReader.Kind.STARTER_GIFT) {
                                    if (FtbQuestReader.starterGiftAvailable(recipient.player(), team, quest.getValue())
                                            && giftRecipients.add(recipient.player().getUUID()))
                                        runtime.players().notifyEvent(recipient.player(), StarterGiftModule.preview(
                                                this.access.gifts.preview(recipient.player(), this.file, team)));
                                } else
                                    runtime.players().notifyEvent(recipient.player(), this.notification(quest.getValue(), false, recipient.player(), team));
                            }
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
                    .map(quest -> FtbQuestAccess.brief(team, quest, 0)).toList());
            runtime.players().notifyEvent(player, new AgentResult("SUCCEEDED", key, "", List.of(new AgentResult.Card(key,
                    ResultTypes.QUEST_STARTUP.of(overview), List.of(new AgentResult.Action("system.action.view_quests",
                    ResultTypes.QUESTS_ACTION, new QuestQueryInput("", false)))))));
        }
    }

    private AgentResult notification(Quest quest, boolean completed, ServerPlayer player, TeamData team) {
        String key = completed ? "system.result.quest_completed" : "system.result.quest_unlocked";
        String id = String.format(Locale.ROOT, "%016x", quest.getId());
        List<AgentResult.Action> actions = new ArrayList<>();
        if (!completed && FtbQuestReader.canComplete(player, team, quest))
            actions.add(QuestSubmitModule.completeAction(id));
        if (completed && this.file.getQuest(quest.getId()) == quest) {
            List<Reward> rewards = quest.getRewards().stream().filter(reward -> FtbQuestReader.canClaim(player, team, reward)).toList();
            // A single ordinary reward can execute directly; choices and multiple rewards retain explicit selection.
            if (rewards.size() == 1 && !(rewards.getFirst() instanceof ChoiceReward)) {
                FtbQuestAccess.reference(player, team, quest, rewards.getFirst(), this.access.references).ifPresent(reference ->
                        actions.add(new AgentResult.Action("system.action.claim", ResultTypes.CLAIM_ACTION, new QuestClaimInput(reference, Optional.empty()))));
            } else if (!rewards.isEmpty())
                actions.add(new AgentResult.Action("system.action.claim", ResultTypes.QUEST_ACTION, QuestDetailInput.byId(id)));
        }
        actions.add(new AgentResult.Action("system.action.quest_detail", ResultTypes.QUEST_ACTION, QuestDetailInput.byId(id)));
        return new AgentResult("SUCCEEDED", key, "", List.of(new AgentResult.Card(key,
                ResultTypes.QUEST_NOTICE.of(new ResultTypes.QuestNotice(id, MiscUtil.clip(quest.getTitle().getString(), 256),
                        MiscUtil.clip(quest.getChapter().getTitle().getString(), 128), quest.getRewards().stream()
                        .filter(reward -> !team.isRewardBlocked(reward) && reward.getAutoClaimType() != RewardAutoClaim.INVISIBLE)
                        .flatMap(reward -> FtbQuestAccess.previewItems(reward).stream()).toList(),
                        !completed && (!quest.getHideTextUntilComplete().get(quest.getChapter().isHideTextUntilComplete()) || team.isCompleted(quest))
                                ? String.join("\n", quest.getRawDescription()) : "",
                        completed ? List.of() : quest.getTasksAsList().stream().map(task -> FtbQuestSpeech.requirement(team, task).text()).toList(),
                        quest.getRewards().stream().filter(reward -> !team.isRewardBlocked(reward)
                                        && reward.getAutoClaimType() != RewardAutoClaim.INVISIBLE && FtbQuestAccess.previewItems(reward).isEmpty())
                                .map(FtbQuestSpeech::rewardText).toList(),
                        !completed && FtbQuestReader.requiresManualSubmission(quest))), actions)));
    }

    // The native event and explicit submission converge on the same pending completion entry.
    static void submitted(ServerPlayer player, ServerQuestFile file, TeamData team, Quest quest) {
        if (FtbQuestReader.kind(quest) != FtbQuestReader.Kind.NORMAL) return;
        AssistantServer runtime = AssistantServer.get(file.server);
        FtbQuestAccess access = ACCESS.get(file.server);
        if (runtime == null || access == null || !runtime.players().notificationsEnabled(player)) return;
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
            return runtime.players().notificationsEnabled(this.player) && runtime.engine().current(this.player.getUUID(), this.incarnation)
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
