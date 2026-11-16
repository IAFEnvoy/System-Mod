package com.iafenvoy.system.screen.gui;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.screen.card.CardRenderer;
import com.sighs.apricityui.init.Element;
import org.apache.commons.lang3.StringEscapeUtils;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.*;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.CoreHtmlNodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.HtmlWriter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * CommonMark owns Markdown structure; AUI parses generated HTML and vanilla handles explicit Markdown links.
 */
@SuppressWarnings("deprecation")
public final class AssistantMarkdown {
    private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create(), StrikethroughExtension.create());
    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS)
            .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES).build();
    private static final Pattern NAMED_ENTITY = Pattern.compile("&[A-Za-z][A-Za-z0-9]*;");
    private static final Pattern LINE_END = Pattern.compile("</(?:p|h[1-6]|pre|blockquote|li|tr|table)>|<br\\s*/?>|<div class=\"markdown_hr\"></div>");
    private static final Pattern CELL_END = Pattern.compile("</(?:th|td)>");
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
            String lines = LINE_END.matcher(CELL_END.matcher(html(source)).replaceAll("\t")).replaceAll("\n");
            // Decode once, after removing only renderer-generated tags; literal HTML remains visible text.
            return StringEscapeUtils.unescapeHtml4(TAG.matcher(lines).replaceAll("").replace("&apos;", "&#39;")).strip();
        } catch (RuntimeException error) {
            SystemMod.LOGGER.warn("Unable to export Markdown, using its source", error);
            return source;
        }
    }

    private static String html(String source) {
        String html = HtmlRenderer.builder().escapeHtml(true)
                .nodeRendererFactory(context -> new CardHtmlRenderer(context, source))
                .attributeProviderFactory(_ -> (node, tag, attributes) -> {
                    String classes = "markdown_" + tag;
                    if (node instanceof TableCell cell && cell.getAlignment() != null)
                        classes += " markdown_align_" + cell.getAlignment().name().toLowerCase(Locale.ROOT);
                    attributes.remove("align");
                    attributes.put("class", classes);
                }).extensions(EXTENSIONS).build().render(PARSER.parse(source));
        // AUI accepts numeric references but only a small subset of HTML named entities; decode exactly once.
        return NAMED_ENTITY.matcher(html).replaceAll(match -> {
            String decoded = StringEscapeUtils.unescapeHtml4(match.group().replace("&apos;", "&#39;"));
            if (decoded.equals(match.group())) return match.group();
            StringBuilder numeric = new StringBuilder();
            decoded.codePoints().forEach(point -> numeric.append("&#").append(point).append(';'));
            return numeric.toString();
        });
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

    /**
     * Per-conversion list counters; links stay inert until AUI has initialized their nodes.
     */
    private static final class CardHtmlRenderer extends CoreHtmlNodeRenderer {
        private final HtmlWriter html;
        private final String source;
        private final Deque<Integer> lists = new ArrayDeque<>();

        private CardHtmlRenderer(HtmlNodeRendererContext context, String source) {
            super(context);
            this.html = context.getWriter();
            this.source = source;
        }

        @Override
        public void visit(OrderedList list) {
            this.lists.push(list.getMarkerStartNumber() == null ? 1 : list.getMarkerStartNumber());
            super.visit(list);
            this.lists.pop();
        }

        @Override
        public void visit(BulletList list) {
            this.lists.push(-1);
            super.visit(list);
            this.lists.pop();
        }

        @Override
        public void visit(ListItem item) {
            int number = this.lists.pop();
            this.lists.push(number < 0 ? -1 : number + 1);
            this.html.tag("li", Map.of("class", "markdown_li"));
            this.html.tag("span", Map.of("class", "markdown_marker"));
            this.html.text(number < 0 ? "• " : number + ". ");
            this.html.tag("/span");
            this.visitChildren(item);
            this.html.tag("/li");
            this.html.line();
        }

        @Override
        public void visit(ThematicBreak line) {
            this.html.raw("<div class=\"markdown_hr\"></div>");
        }

        @Override
        public void visit(Link link) {
            // Autolinks remain literal; raw HTML is escaped by the core renderer.
            if (!link.getSourceSpans().isEmpty()) {
                SourceSpan span = link.getSourceSpans().getFirst();
                if (this.source.charAt(span.getInputIndex()) == '<') {
                    this.html.text(this.source.substring(span.getInputIndex(), span.getInputIndex() + span.getLength()));
                    return;
                }
            }
            this.html.tag("span", Map.of("class", "markdown_a", "href", link.getDestination()));
            this.visitChildren(link);
            this.html.tag("/span");
        }

        @Override
        public void visit(Image image) {
            image.accept(new AbstractVisitor() {
                @Override
                public void visit(Text text) {
                    CardHtmlRenderer.this.html.text(text.getLiteral());
                }

                @Override
                public void visit(Code code) {
                    CardHtmlRenderer.this.html.text(code.getLiteral());
                }
            });
        }
    }
}
