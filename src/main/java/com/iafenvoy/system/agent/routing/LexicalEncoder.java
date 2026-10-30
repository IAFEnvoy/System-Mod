package com.iafenvoy.system.agent.routing;

import com.iafenvoy.system.util.CodecIO;
import com.mojang.serialization.Codec;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Reviewed domain aliases plus sparse word/character features, without trained weights or JNI.
 */
final class LexicalEncoder {
    private static final Pattern ITEM_ID = Pattern.compile("(?<![a-z0-9_.:/-])[a-z0-9_.-]+:[a-z0-9_./-]+(?![a-z0-9_.:/-])");

    record Features(Map<String, Double> terms, Set<String> topics) {
    }

    private record Alias(String text, String concept) {
    }

    private final Map<String, List<Alias>> aliases;
    private final boolean domainAliases;

    LexicalEncoder() throws IOException {
        this(true);
    }

    LexicalEncoder(boolean domainAliases) throws IOException {
        this.domainAliases = domainAliases;
        if (!domainAliases) {
            this.aliases = Map.of();
            return;
        }
        try (InputStream stream = LexicalEncoder.class.getResourceAsStream("/assets/system/routing/lexicon.json")) {
            if (stream == null) throw new IOException("Missing routing lexicon");
            Codec<Map<String, Map<String, List<String>>>> codec = Codec.unboundedMap(Codec.STRING,
                    Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()));
            byte[] bytes = stream.readNBytes(131073);
            if (bytes.length > 131072) throw new IOException("Routing lexicon exceeds byte limit");
            Map<String, Map<String, List<String>>> root = CodecIO.parse(codec, new String(bytes, StandardCharsets.UTF_8));
            Map<String, List<Alias>> languages = new HashMap<>();
            for (Map.Entry<String, Map<String, List<String>>> language : root.entrySet()) {
                List<Alias> words = new ArrayList<>();
                language.getValue().forEach((concept, aliases) -> aliases.forEach(word -> words.add(new Alias(normalize(word), concept))));
                words.sort(Comparator.comparingInt((Alias alias) -> alias.text().length()).reversed());
                if (words.stream().anyMatch(word -> word.text().isBlank()))
                    throw new IOException("Empty routing alias");
                languages.put(language.getKey(), List.copyOf(words));
            }
            this.aliases = Map.copyOf(languages);
        }
    }

    Features encode(String input, String language) {
        String text = normalize(input);
        if (this.domainAliases) text = ITEM_ID.matcher(text).replaceAll(" registryitem ");
        List<Alias> dictionary = this.aliases.getOrDefault(language, List.of());
        Map<String, Double> terms = new HashMap<>();
        Set<String> topics = new HashSet<>();
        List<String> sequence = new ArrayList<>();
        for (int offset = 0; offset < text.length(); ) {
            int point = text.codePointAt(offset);
            if (!Character.isLetterOrDigit(point)) {
                offset += Character.charCount(point);
                continue;
            }
            Alias found = null;
            for (Alias alias : dictionary)
                if (text.startsWith(alias.text(), offset) && boundaries(text, offset, alias.text())) {
                    found = alias;
                    break;
                }
            if (found != null) {
                if (!found.concept().equals("ignore")) {
                    String term = "term:" + found.concept();
                    terms.merge(term, 1.0, Double::sum);
                    sequence.add(term);
                    if (!found.concept().equals("self") && !found.concept().equals("registryitem")) topics.add(term);
                }
                offset += found.text().length();
            } else if (Character.UnicodeScript.of(point) == Character.UnicodeScript.HAN) {
                // Keep OOV characters in the query norm instead of silently dropping them.
                String character = new String(Character.toChars(point));
                terms.merge("char:" + character, 1.0, Double::sum);
                sequence.add("char:" + character);
                topics.add("char:" + character);
                offset += Character.charCount(point);
            } else {
                int end = offset + Character.charCount(point);
                while (end < text.length() && Character.isLetterOrDigit(text.codePointAt(end))
                        && Character.UnicodeScript.of(text.codePointAt(end)) != Character.UnicodeScript.HAN)
                    end += Character.charCount(text.codePointAt(end));
                String word = "word:" + text.substring(offset, end);
                terms.merge(word, 1.0, Double::sum);
                sequence.add(word);
                topics.add(word);
                offset = end;
            }
        }
        for (int i = 1; i < sequence.size(); i++)
            terms.merge("pair:" + sequence.get(i - 1) + "/" + sequence.get(i), 0.35, Double::sum);
        return new Features(Map.copyOf(terms), Set.copyOf(topics));
    }

    private static boolean boundaries(String text, int offset, String alias) {
        int end = offset + alias.length();
        return (!latin(alias.charAt(0)) || offset == 0 || !latin(text.charAt(offset - 1)))
                && (!latin(alias.charAt(alias.length() - 1)) || end == text.length() || !latin(text.charAt(end)));
    }

    private static boolean latin(char character) {
        return character >= 'a' && character <= 'z' || character >= '0' && character <= '9' || character == '_';
    }

    static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT).strip().replaceAll("[\\t ]+", " ");
    }
}
