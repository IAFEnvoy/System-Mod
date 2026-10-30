package com.iafenvoy.system.compat.ftb;

import com.google.gson.JsonParseException;
import com.iafenvoy.system.util.LegacyText;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.translation.TranslationKey;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Resolve each recipient's FTB translations without changing the shared quest file locale or title cache.
 */
final class FtbQuestText {
    private FtbQuestText() {
    }

    static Component title(ServerPlayer player, QuestObjectBase object) {
        return title(player.registryAccess(), player.clientInformation().language(), object);
    }

    static Component title(HolderLookup.Provider registries, String language, QuestObjectBase object) {
        String raw = object.getQuestFile().getTranslationManager()
                .getStringTranslation(object, language, TranslationKey.TITLE).orElse("");
        if (!raw.isEmpty()) return parse(registries, raw);
        // Native unnamed quests inherit their first task's title; that title also needs the player's locale.
        if (object instanceof Quest quest && !quest.getTasksAsList().isEmpty())
            return title(registries, language, quest.getTasksAsList().getFirst());
        return LegacyText.component(object.getAltTitle());
    }

    static String description(ServerPlayer player, TeamData team, Quest quest) {
        if (quest.getHideTextUntilComplete().get(quest.getChapter().isHideTextUntilComplete()) && !team.isCompleted(quest))
            return "";
        return rawDescription(player, quest);
    }

    // Shared local indexes store static text only; description remains the permission-aware display entry.
    static String rawDescription(ServerPlayer player, Quest quest) {
        return rawDescription(player.registryAccess(), player.clientInformation().language(), quest);
    }

    static String rawDescription(HolderLookup.Provider registries, String language, Quest quest) {
        List<String> lines = quest.getQuestFile().getTranslationManager()
                .getStringListTranslation(quest, language, TranslationKey.QUEST_DESC).orElse(List.of());
        return String.join("\n", lines.stream().map(line -> LegacyText.formatted(parse(registries, line))).toList());
    }

    private static Component parse(HolderLookup.Provider registries, String raw) {
        String text = raw.strip();
        if (text.startsWith("{") || text.startsWith("[") || text.startsWith("\"")) try {
            // FTB's TextUtils parser calls client-only classes; vanilla's lenient parser is server-safe.
            Component component = Component.Serializer.fromJsonLenient(text, registries);
            if (component != null) return LegacyText.component(component);
        } catch (JsonParseException ignored) { /* Non-JSON quest text remains literal. */ }
        return LegacyText.component(Component.literal(raw));
    }
}
