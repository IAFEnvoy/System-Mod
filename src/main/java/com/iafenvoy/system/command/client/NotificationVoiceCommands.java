package com.iafenvoy.system.command.client;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.routing.LexicalIndex;
import com.iafenvoy.system.screen.AssistantMessages.Button;
import com.iafenvoy.system.util.CodecIO;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Fixed button labels own reusable example vectors; only buttons in this notification can execute.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class NotificationVoiceCommands {
    private static final Identifier RESOURCE = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "routing/notification-buttons.json");
    private static final Codec<Map<String, Map<String, List<String>>>> CODEC = Codec.unboundedMap(Codec.STRING,
            Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()));
    private static final Pattern COMPLEX = Pattern.compile("不|别|没|如果|是否|能否|怎么|如何|为什么|然后|并且|同时|或者|以后|稍后|"
            + "\\b(?:not|no|never|don't|cannot|can't|without|if|unless|then|also|and|or|why|later)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PREFIX = Pattern.compile("^(?:请帮我|麻烦帮我|帮我|请|麻烦|点击|选择|执行|按下|please\\s+|click\\s+|select\\s+|press\\s+)");
    private static final Pattern SUFFIX = Pattern.compile("(?:按钮|一下|吧|\\s+button)$");
    private static Map<String, Map<String, List<String>>> examples = Map.of();
    private static List<String> indexedLabels = List.of();
    private static String indexedLanguage = "";
    private static LexicalIndex<String> index;

    private NotificationVoiceCommands() {
    }

    @SubscribeEvent
    public static void reloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "notification_voice"),
                (ResourceManagerReloadListener) NotificationVoiceCommands::reload);
    }

    private static void reload(ResourceManager manager) {
        Map<String, Map<String, List<String>>> loaded = new LinkedHashMap<>();
        try {
            for (Resource resource : manager.getResourceStack(RESOURCE))
                try (InputStream stream = resource.open()) {
                    Map<String, Map<String, List<String>>> layer = CodecIO.parse(CODEC, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                    layer.forEach((key, languages) -> {
                        Map<String, List<String>> combined = new LinkedHashMap<>(loaded.getOrDefault(key, Map.of()));
                        combined.putAll(languages);
                        loaded.put(key, Map.copyOf(combined));
                    });
                }
            examples = Map.copyOf(loaded);
            indexedLabels = List.of();
            indexedLanguage = "";
            index = null;
        } catch (Exception error) {
            SystemMod.LOGGER.warn("Notification voice examples failed to reload; retaining previous definitions", error);
        }
    }

    public static List<Button> match(String text, List<Button> buttons, String language) {
        String normalized = normalize(text);
        Set<String> forms = forms(normalized);
        List<Button> exact = buttons.stream().filter(button -> forms.contains(normalize(button.label()))
                || examples.getOrDefault(button.labelKey(), Map.of()).getOrDefault(language, List.of()).stream()
                .anyMatch(example -> forms.contains(normalize(example)))).toList();
        if (!exact.isEmpty()) return exact;
        if (COMPLEX.matcher(text).find()) return List.of();
        prepare(buttons, language);
        if (index == null) return List.of();
        LexicalIndex.Ranking<String> ranking = index.search(normalized, language);
        if (ranking.matches().isEmpty()) return List.of();
        LexicalIndex.Match<String> best = ranking.matches().getFirst();
        double second = ranking.matches().size() < 2 ? 0 : ranking.matches().get(1).score();
        // Similarity is not a probability. Unknown content and close alternatives stay with normal chat routing.
        if (best.score() < 0.9 || best.coverage() < 0.9 || !best.topicsCovered() || best.score() - second < 0.15)
            return List.of();
        return buttons.stream().filter(button -> button.labelKey().equals(best.value())).toList();
    }

    public static void prepare(List<Button> buttons, String language) {
        List<String> labels = buttons.stream().map(Button::labelKey).distinct().sorted().toList();
        if (labels.equals(indexedLabels) && language.equals(indexedLanguage)) return;
        indexedLabels = labels;
        indexedLanguage = language;
        List<LexicalIndex.Example<String>> documents = new ArrayList<>();
        for (Button button : buttons) {
            documents.add(new LexicalIndex.Example<>(button.labelKey(), language, button.label()));
            examples.getOrDefault(button.labelKey(), Map.of()).getOrDefault(language, List.of())
                    .forEach(text -> documents.add(new LexicalIndex.Example<>(button.labelKey(), language, text)));
        }
        try {
            // Keep verbs literal: the server's broad "write" concept must not equate buying with claiming.
            index = new LexicalIndex<>(documents, false);
        } catch (Exception error) {
            index = null;
            SystemMod.LOGGER.warn("Notification voice index unavailable; retaining exact labels and chat fallback", error);
        }
    }

    private static Set<String> forms(String text) {
        Set<String> forms = new HashSet<>();
        String value = normalize(text);
        while (!value.isEmpty() && forms.add(value)) {
            String next = PREFIX.matcher(value).replaceFirst("");
            if (next.equals(value)) next = SUFFIX.matcher(value).replaceFirst("");
            value = next.strip();
        }
        return forms;
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("§[0-9a-fk-or]", "").replaceAll("[\\p{P}\\p{Z}\\s]+", " ").strip();
    }
}
