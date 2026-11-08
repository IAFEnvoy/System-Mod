package com.iafenvoy.system.screen.gui;

import com.github.rjeschke.txtmark.Configuration;
import com.github.rjeschke.txtmark.DefaultDecorator;
import com.github.rjeschke.txtmark.Processor;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.screen.card.CardRenderer;
import com.sighs.apricityui.init.Element;
import org.apache.commons.lang3.StringEscapeUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Txtmark owns Markdown structure; AUI parses its generated HTML and vanilla handles explicit Markdown links.
 */
@SuppressWarnings("deprecation")
public final class AssistantMarkdown {
    private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,})(.*)$");
    private static final Pattern SOURCE_TAG = Pattern.compile("<(?:/?[A-Za-z][\\w:-]*(?=[\\s/>])|[!?])(?:\"[^\"]*\"|'[^']*'|[^'\">])*>");
    private static final Pattern NAMED_ENTITY = Pattern.compile("&[A-Za-z][A-Za-z0-9]*;");
    private static final Pattern BLOCK_TAG = Pattern.compile("<(p|h[1-6]|pre|blockquote|ul|ol|li|strong|em|sup|code)\\b[^>]*>");
    private static final Pattern IMAGE = Pattern.compile("<span class=\"markdown_image\" src=\"[^\"]*\" alt=\"([^\"]*)\"(?: title=\"[^\"]*\")?></span>");
    private static final Pattern LINE_END = Pattern.compile("</(?:p|h[1-6]|pre|blockquote|li)>|<br\\s*/?>|<div class=\"markdown_hr\"></div>");
    private static final Pattern TAG = Pattern.compile("<[^>]*>");

    private AssistantMarkdown() {
    }

    public static Element render(CardRenderer.Render view, String source) {
        Element root = Element.init(view.element("div", ""));
        try {
            root.setInnerHTML(html(source));
            for (Element element : root.querySelectorAll(".markdown_a")) {
                String url = url(element.getAttribute("href"));
                if (url.isEmpty()) continue;
                element.setClassName("markdown_a markdown_link");
                element.setAttribute("role", "link");
                element.setAttribute("title", url);
                element.addEventListener("click", event -> {
                    event.preventDefault();
                    event.stopPropagation();
                    if (view.interactive()) AssistantPageScreen.openLink(url);
                });
            }
        } catch (RuntimeException error) {
            SystemMod.LOGGER.warn("Unable to render Markdown, displaying its source", error);
            root.setTextContent(source);
        }
        return root;
    }

    public static String text(String source) {
        try {
            String lines = LINE_END.matcher(html(source)).replaceAll("\n");
            // Decode once, after removing only renderer-generated tags; literal HTML remains visible text.
            return StringEscapeUtils.unescapeHtml4(TAG.matcher(lines).replaceAll("").replace("&apos;", "&#39;")).strip();
        } catch (RuntimeException error) {
            SystemMod.LOGGER.warn("Unable to export Markdown, using its source", error);
            return source;
        }
    }

    private static String html(String source) {
        String marker = "\uE000";
        while (source.contains(marker)) marker += "\uE001";
        String prefix = marker;
        List<String> literals = new ArrayList<>();
        // Keep source tags literal, including their quotes, before Txtmark's smart punctuation runs.
        String markdown = SOURCE_TAG.matcher(normalizeAdjacentFences(source)).replaceAll(match -> {
            String token = prefix + literals.size() + "\uE002";
            literals.add(match.group().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"));
            return Matcher.quoteReplacement(token);
        });
        // Defer '<' escaping so panic mode cannot double-escape code or literal '&lt;'.
        String lessThan = prefix + "lt\uE002";
        markdown = markdown.replace("<", lessThan);
        Configuration configuration = Configuration.builder().forceExtentedProfile().enableSafeMode().enablePanicMode()
                .setDecorator(new CardDecorator()).build();
        String html = Processor.process(markdown, configuration);
        // These patterns see only Txtmark-generated tags. Discard extended-profile heading/list IDs as well.
        html = BLOCK_TAG.matcher(html).replaceAll("<$1 class=\"markdown_$1\">");
        html = IMAGE.matcher(html).replaceAll(match -> Matcher.quoteReplacement("<span>" + match.group(1) + "</span>"));
        // AUI accepts numeric references but only a small subset of HTML named entities; decode exactly once.
        html = NAMED_ENTITY.matcher(html).replaceAll(match -> {
            String decoded = StringEscapeUtils.unescapeHtml4(match.group().replace("&apos;", "&#39;"));
            if (decoded.equals(match.group())) return match.group();
            StringBuilder numeric = new StringBuilder();
            decoded.codePoints().forEach(point -> numeric.append("&#").append(point).append(';'));
            return numeric.toString();
        });
        html = Pattern.compile(Pattern.quote(prefix) + "(\\d+)\uE002").matcher(html)
                .replaceAll(match -> Matcher.quoteReplacement(literals.get(Integer.parseInt(match.group(1)))));
        return html.replace(lessThan, "&lt;");
    }

    private static String normalizeAdjacentFences(String source) {
        List<String> lines = new ArrayList<>();
        String fence = "";
        boolean afterFence = false;
        for (String line : source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            Matcher match = FENCE.matcher(line);
            if (match.matches()) {
                if (fence.isEmpty()) {
                    // Txtmark otherwise keeps the second opener as code when consecutive blocks have a blank gap.
                    if (afterFence) while (!lines.isEmpty() && lines.getLast().isBlank()) lines.removeLast();
                    fence = match.group(1);
                    afterFence = false;
                } else if (match.group(1).charAt(0) == fence.charAt(0) && match.group(1).length() >= fence.length() && match.group(2).isBlank()) {
                    fence = "";
                    afterFence = true;
                    lines.add(line);
                    continue;
                }
            } else if (!line.isBlank()) afterFence = false;
            lines.add(line);
        }
        return String.join("\n", lines);
    }

    private static String url(String href) {
        if (href == null) return "";
        try {
            URI uri = new URI(href.strip());
            String scheme = uri.getScheme();
            return scheme != null && (scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))
                    && uri.getHost() != null ? uri.toASCIIString() : "";
        } catch (URISyntaxException ignored) {
            return "";
        }
    }

    /** Per-conversion list counters; generated links and images stay inert while AUI initializes their nodes. */
    private static final class CardDecorator extends DefaultDecorator {
        private final Deque<Integer> lists = new ArrayDeque<>();
        private final Deque<Item> items = new ArrayDeque<>();

        private record Item(int start, String marker) {
        }

        @Override
        public void openOrderedList(StringBuilder out) {
            this.lists.push(1);
            super.openOrderedList(out);
        }

        @Override
        public void closeOrderedList(StringBuilder out) {
            this.lists.pop();
            super.closeOrderedList(out);
        }

        @Override
        public void openUnorderedList(StringBuilder out) {
            this.lists.push(0);
            super.openUnorderedList(out);
        }

        @Override
        public void closeUnorderedList(StringBuilder out) {
            this.lists.pop();
            super.closeUnorderedList(out);
        }

        @Override
        public void openListItem(StringBuilder out) {
            int number = this.lists.pop();
            this.lists.push(number == 0 ? 0 : number + 1);
            this.items.push(new Item(out.length(), number == 0 ? "• " : number + ". "));
            super.openListItem(out);
        }

        @Override
        public void closeListItem(StringBuilder out) {
            Item item = this.items.pop();
            out.insert(out.indexOf(">", item.start()) + 1, "<span class=\"markdown_marker\">" + item.marker() + "</span>");
            super.closeListItem(out);
        }

        @Override
        public void horizontalRuler(StringBuilder out) {
            out.append("<div class=\"markdown_hr\"></div>");
        }

        @Override
        public void openLink(StringBuilder out) {
            out.append("<span class=\"markdown_a\"");
        }

        @Override
        public void closeLink(StringBuilder out) {
            out.append("</span>");
        }

        @Override
        public void openImage(StringBuilder out) {
            out.append("<span class=\"markdown_image\"");
        }

        @Override
        public void closeImage(StringBuilder out) {
            out.append("></span>");
        }
    }
}
