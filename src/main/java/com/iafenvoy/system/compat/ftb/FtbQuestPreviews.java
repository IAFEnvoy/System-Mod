package com.iafenvoy.system.compat.ftb;

import com.iafenvoy.system.agent.builtin.output.ItemPreviewOutput;
import com.iafenvoy.system.agent.builtin.output.QuestIconOutput;
import com.iafenvoy.system.util.ItemStackTemplate;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.reward.*;
import dev.ftb.mods.ftbquests.quest.task.Task;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * Shared previews retain native type icons and server-prepared quantities; gift summaries merge compatible rewards.
 */
final class FtbQuestPreviews {
    private FtbQuestPreviews() {
    }

    static List<QuestIconOutput> task(ServerPlayer player, TeamData team, Task task) {
        if (!FtbQuestAccess.previewItems(task).isEmpty()) return List.of();
        long progress = team.getProgress(task);
        long required = task.getMaxProgress();
        return List.of(new QuestIconOutput(QuestIconOutput.Kind.TASK, task.getType().getTypeId(),
                FtbQuestText.title(player, task), progress + "/" + required,
                List.of(FtbQuestSpeech.requirement(player, team, task).text(),
                        Component.translatable("system.ui.quest_icon_progress", progress, required).withStyle(ChatFormatting.GRAY))));
    }

    static List<QuestIconOutput> reward(ServerPlayer player, TeamData team, Reward reward) {
        return reward(player, team, reward, reward);
    }

    static List<QuestIconOutput> reward(ServerPlayer player, TeamData team, Reward reward, Reward claimOwner) {
        if (!FtbQuestAccess.previewItems(reward).isEmpty()) return List.of();
        // A choice candidate belongs to the outer claim, not to an independently claimable table entry.
        String state = team.getClaimType(player.getUUID(), claimOwner).name().toLowerCase(Locale.ROOT);
        return List.of(new QuestIconOutput(QuestIconOutput.Kind.REWARD, reward.getType().getTypeId(),
                FtbQuestText.title(player, reward), reward.getButtonText(),
                List.of(FtbQuestSpeech.rewardText(player, reward), Component.translatable("system.state." + state).withStyle(ChatFormatting.GRAY))));
    }

    static List<ItemPreviewOutput> giftItems(ServerPlayer player, List<Reward> rewards) {
        Map<ItemStackTemplate, ItemAmount> totals = new LinkedHashMap<>();
        List<ItemPreviewOutput> separate = new ArrayList<>();
        for (Reward reward : rewards) {
            if (!(reward instanceof ItemReward item) || item.getItem().isEmpty()) continue;
            ItemStackTemplate template = ItemStackTemplate.fromNonEmptyStack(item.getItem()).withCount(1);
            CompoundTag data = data(player, reward);
            ItemAmount amount = new ItemAmount(item.getCount(), (long) item.getCount() + data.getInt("random_bonus"));
            // Inventory-conditional rewards stay separate; identical component patches are required for merging.
            if (data.getBoolean("only_one")) separate.add(new ItemPreviewOutput(template, amount.text()));
            else totals.merge(template, amount, ItemAmount::add);
        }
        List<ItemPreviewOutput> result = new ArrayList<>();
        totals.forEach((item, amount) -> result.add(new ItemPreviewOutput(item, amount.text())));
        result.addAll(separate);
        return List.copyOf(result);
    }

    static List<QuestIconOutput> giftIcons(ServerPlayer player, List<Reward> rewards) {
        Map<RewardType, Long> totals = new LinkedHashMap<>();
        for (Reward reward : rewards) {
            boolean xp = reward.getClass() == XPReward.class;
            if (!xp && reward.getClass() != XPLevelsReward.class) continue;
            // FTB exposes no numeric XP getter; use its server-safe saved fields instead of client button text.
            long amount = data(player, reward).getInt(xp ? "xp" : "xp_levels");
            totals.merge(reward.getType(), amount, Long::sum);
        }
        return totals.entrySet().stream().map(entry -> {
            String name = entry.getKey() == RewardTypes.XP ? "xp" : "xp_levels";
            String speech = entry.getKey() == RewardTypes.XP ? "xp" : "levels";
            long amount = entry.getValue();
            return new QuestIconOutput(QuestIconOutput.Kind.REWARD, entry.getKey().getTypeId(),
                    Component.translatable("ftbquests.reward.ftbquests." + name), (amount >= 0 ? "+" : "") + amount,
                    List.of(Component.translatable("system.tts.quest." + speech, amount)));
        }).toList();
    }

    private static CompoundTag data(ServerPlayer player, Reward reward) {
        CompoundTag data = new CompoundTag();
        reward.writeData(data, player.registryAccess());
        return data;
    }

    private record ItemAmount(long minimum, long maximum) {
        ItemAmount add(ItemAmount other) {
            return new ItemAmount(this.minimum + other.minimum, this.maximum + other.maximum);
        }

        String text() {
            return this.minimum == this.maximum ? Long.toString(this.minimum) : this.minimum + "-" + this.maximum;
        }
    }
}
