package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.QuestNarration;
import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.builtin.output.QuestStartupOutput;
import com.iafenvoy.system.agent.builtin.output.QuestSubmission;
import com.iafenvoy.system.agent.builtin.quest.StarterGiftModule;
import com.iafenvoy.system.assistant.AssistantServer;
import com.iafenvoy.system.card.CardUpdate;
import com.iafenvoy.system.module.builtin.QuestSystemModule;
import com.iafenvoy.system.network.ServerNetworkHandler;
import com.iafenvoy.system.util.LegacyText;
import dev.ftb.mods.ftbquests.events.ClearFileCacheEvent;
import dev.ftb.mods.ftbquests.events.ObjectCompletedEvent;
import dev.ftb.mods.ftbquests.events.ObjectStartedEvent;
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
        // 1.21.1 exposes progress through Architectury events instead of the unified 26.x FTBQuestsEvent bus.
        ObjectCompletedEvent.QUEST.register(event -> {
            FtbQuestNotifications tracker = changed(event);
            if (tracker != null) tracker.completed(event);
            return dev.architectury.event.EventResult.pass();
        });
        ObjectStartedEvent.QUEST.register(event -> {
            changed(event);
            return dev.architectury.event.EventResult.pass();
        });
        ObjectCompletedEvent.TASK.register(event -> {
            changed(event);
            return dev.architectury.event.EventResult.pass();
        });
        ObjectCompletedEvent.CHAPTER.register(event -> {
            changed(event);
            return dev.architectury.event.EventResult.pass();
        });
        ObjectCompletedEvent.FILE.register(event -> {
            changed(event);
            return dev.architectury.event.EventResult.pass();
        });
        ClearFileCacheEvent.EVENT.register(file -> {
            if (file instanceof ServerQuestFile questFile) {
                FtbQuestNotifications tracker = SERVERS.get(questFile.server);
                if (tracker != null) tracker.teams.clear();
            }
        });
    }

    static void close(MinecraftServer server) {
        SERVERS.remove(server);
        ACCESS.remove(server);
    }

    static void remove(ServerPlayer player) {
        FtbQuestNotifications tracker = SERVERS.get(player.level().getServer());
        if (tracker == null) return;
        for (TeamState state : tracker.teams.values()) {
            Map<UUID, Recipient> recipients = new HashMap<>(state.recipients);
            recipients.remove(player.getUUID());
            state.recipients = Map.copyOf(recipients);
            state.completed.replaceAll((unused1, completion) -> new Completion(completion.quest(),
                    completion.recipients().stream().filter(recipient -> recipient.player() != player).toList()));
        }
    }

    private static FtbQuestNotifications changed(ObjectCompletedEvent<?> event) {
        return changed(event.getData(), event.getObject());
    }

    private static FtbQuestNotifications changed(ObjectStartedEvent<?> event) {
        return changed(event.getData(), event.getObject());
    }

    private static FtbQuestNotifications changed(TeamData teamData, QuestObjectBase object) {
        // Client-side progress echoes use the same event types and must never enter this bridge.
        if (!(object.getQuestFile() instanceof ServerQuestFile file)) return null;
        FtbQuestNotifications tracker = SERVERS.get(file.server);
        if (tracker == null || tracker.file != file) return null;
        TeamState state = tracker.teams.get(teamData.getTeamId());
        if (state == null || state.team != teamData) return null;
        state.dirty = true;
        if (!QuestObjectBase.shouldSendNotifications()) state.silent = true;
        return tracker;
    }

    private void completed(ObjectCompletedEvent.QuestEvent event) {
        TeamState state = this.teams.get(event.getData().getTeamId());
        Quest quest = event.getObject();
        if (state == null || state.silent || state.team.isLocked() || !visible(state.team, quest)
                || FtbQuestReader.kind(quest) != FtbQuestReader.Kind.NORMAL) return;
        AssistantServer runtime = AssistantServer.get(this.file.server);
        if (runtime == null) return;
        List<Recipient> recipients = new ArrayList<>();
        for (ServerPlayer player : event.getOnlineMembers()) {
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
        if (ServerQuestFile.getInstance().isEmpty()) return;
        ServerQuestFile file = ServerQuestFile.getInstance().orElseThrow();
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
            runtime.players().notifyEvent(player, new AgentResult(AgentResult.Status.SUCCEEDED, key, "", List.of(CardUpdate.show(overview))));
        }
    }

    private AgentResult notification(Quest quest, boolean completed, ServerPlayer player, TeamData team) {
        String key = completed ? "system.result.quest_completed" : "system.result.quest_unlocked";
        String id = String.format(Locale.ROOT, "%016x", quest.getId());
        List<Reward> claimable = completed ? quest.getRewards().stream().filter(reward -> FtbQuestReader.canClaim(player, team, reward)).toList() : List.of();
        Optional<String> claimRef = claimable.size() == 1 && !(claimable.getFirst() instanceof ChoiceReward)
                ? FtbQuestAccess.reference(player, team, quest, claimable.getFirst(), this.access.references) : Optional.empty();
        QuestNarration.Quest snapshot = FtbQuestSpeech.snapshot(team, quest);
        return AgentResult.data(key, new ResultTypes.QuestNotice(id, LegacyText.component(quest.getTitle()), LegacyText.component(quest.getChapter().getTitle()),
                quest.getRewards().stream().filter(reward -> !team.isRewardBlocked(reward) && reward.getAutoClaimType() != RewardAutoClaim.INVISIBLE)
                        .flatMap(reward -> FtbQuestAccess.previewItems(reward).stream()).toList(),
                completed ? "" : snapshot.description(),
                completed ? List.of() : snapshot.requirements().stream().map(QuestNarration.Line::text).toList(),
                quest.getRewards().stream().filter(reward -> !team.isRewardBlocked(reward) && reward.getAutoClaimType() != RewardAutoClaim.INVISIBLE
                        && FtbQuestAccess.previewItems(reward).isEmpty()).map(FtbQuestSpeech::rewardText).toList(),
                !completed && FtbQuestReader.requiresManualSubmission(quest),
                completed ? ResultTypes.QuestEvent.COMPLETED : ResultTypes.QuestEvent.UNLOCKED,
                completed ? QuestSubmission.NONE : FtbQuestReader.submissionAction(player, team, quest), !claimable.isEmpty(), claimRef,
                FtbQuestSpeech.content(player, team, quest, completed ? "completed" : "unlocked")));
    }

    // The native event and explicit submission converge on the same pending completion entry.
    static void submitted(ServerPlayer player, ServerQuestFile file, TeamData team, Quest quest) {
        if (FtbQuestReader.kind(quest) != FtbQuestReader.Kind.NORMAL) return;
        AssistantServer runtime = AssistantServer.get(file.server);
        FtbQuestAccess access = ACCESS.get(file.server);
        if (runtime == null || access == null || !runtime.players().notificationsEnabled(player)
                || !runtime.moduleManager().hasType(player.getUUID(), QuestSystemModule.CODEC)) return;
        FtbQuestNotifications tracker = SERVERS.computeIfAbsent(file.server, unused -> new FtbQuestNotifications(file, access));
        TeamState state = tracker.teams.computeIfAbsent(team.getTeamId(), unused -> new TeamState(team, tracker.available(team), Map.of()));
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
