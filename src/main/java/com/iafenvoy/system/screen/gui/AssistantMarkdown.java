package com.iafenvoy.system.screen.gui;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.screen.card.CardRenderer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.util.Markdown;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * AUI parses Markdown, while this inert HTML reader rebuilds only known nodes and vanilla URL actions.
 * Model HTML, attributes and remote images never enter an AUI HTML parser or resource loader.
 */
public final class AssistantMarkdown {
    private static final Pattern LEGACY_CODES = Pattern.compile("(?i)§[0-9a-fk-orx]");
    private static final Set<String> BLOCKS = Set.of("p", "h1", "h2", "h3", "h4", "h5", "h6", "pre", "blockquote", "ul", "ol", "li", "hr");
    private static final Set<String> INLINE = Set.of("strong", "b", "em", "i", "code", "s", "strike", "del", "a");
    private static final Set<String> HIDDEN = Set.of("head", "script", "style", "iframe", "object", "applet");

    private AssistantMarkdown() {
    }

    public static Element render(CardRenderer.Render view, String source) {
        return parse(source, view).root;
    }

    public static String text(String source) {
        return parse(source, null).text.toString().strip();
    }

    private static Reader parse(String source, CardRenderer.Render view) {
        Reader reader = new Reader(view);
        // Strip only section-sign formatting; ampersands in code, entities and URL queries remain literal.
        String markdown = LEGACY_CODES.matcher(source).replaceAll("");
        try {
            new ParserDelegator().parse(new StringReader(Markdown.renderSafe(markdown)), reader, true);
        } catch (IOException | RuntimeException error) {
            SystemMod.LOGGER.warn("Unable to render Markdown, displaying its source", error);
            reader.text.setLength(0);
            reader.text.append(markdown);
            if (reader.root != null) reader.root.setTextContent(markdown);
        }
        return reader;
    }

    private static String url(MutableAttributeSet attributes) {
        Object href = attributes.getAttribute(HTML.Attribute.HREF);
        if (href == null) return "";
        try {
            URI uri = new URI(href.toString().strip());
            String scheme = uri.getScheme();
            return scheme != null && (scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))
                    && uri.getHost() != null ? uri.toASCIIString() : "";
        } catch (URISyntaxException ignored) {
            return "";
        }
    }

    private static final class Frame {
        private final String tag;
        private final Element element;
        private final boolean hidden;
        private int number = 1;

        private Frame(String tag, Element element, boolean hidden) {
            this.tag = tag;
            this.element = element;
            this.hidden = hidden;
        }
    }

    private static final class Reader extends HTMLEditorKit.ParserCallback {
        private final CardRenderer.Render view;
        private final Element root;
        private final Deque<Frame> frames = new ArrayDeque<>();
        private final StringBuilder text = new StringBuilder();

        private Reader(CardRenderer.Render view) {
            this.view = view;
            this.root = view == null ? null : Element.init(view.element("div", ""));
            this.frames.push(new Frame("root", this.root, false));
        }

        @Override
        public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
            String name = tag.toString().toLowerCase(Locale.ROOT);
            Frame parent = this.frames.peek();
            boolean hidden = parent.hidden || HIDDEN.contains(name);
            Element element = parent.element;
            if (!hidden && (BLOCKS.contains(name) || INLINE.contains(name))) {
                if (BLOCKS.contains(name)) this.line();
                element = this.element(parent.element, BLOCKS.contains(name) ? "div" : "span", "markdown_" + name);
                if (name.equals("li")) {
                    Frame list = this.frames.stream().filter(frame -> frame.tag.equals("ol") || frame.tag.equals("ul")).findFirst().orElse(null);
                    String marker = list != null && list.tag.equals("ol") ? list.number++ + ". " : "• ";
                    this.text.append(marker);
                    Element bullet = this.element(element, "span", "markdown_marker");
                    if (bullet != null) bullet.setTextContent(marker);
                } else if (name.equals("a")) this.link(element, url(attributes));
            }
            this.frames.push(new Frame(name, element, hidden));
        }

        @Override
        public void handleEndTag(HTML.Tag tag, int position) {
            String name = tag.toString().toLowerCase(Locale.ROOT);
            if (this.frames.stream().noneMatch(frame -> frame.tag.equals(name))) return;
            Frame frame;
            do frame = this.frames.pop();
            while (!frame.tag.equals(name) && this.frames.size() > 1);
            if (!frame.hidden && BLOCKS.contains(name)) this.line();
        }

        @Override
        public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
            if (this.frames.peek().hidden) return;
            if (tag == HTML.Tag.BR) {
                this.text.append('\n');
                this.element(this.frames.peek().element, "br", "");
            } else if (tag == HTML.Tag.HR) {
                this.line();
                this.element(this.frames.peek().element, "div", "markdown_hr");
            } else if (tag == HTML.Tag.IMG) {
                Object alt = attributes.getAttribute(HTML.Attribute.ALT);
                if (alt != null) this.handleText(alt.toString().toCharArray(), position);
            }
        }

        @Override
        public void handleText(char[] data, int position) {
            Frame frame = this.frames.peek();
            if (frame.hidden) return;
            String value = new String(data);
            this.text.append(value);
            if (frame.element != null) frame.element.appendChild(this.view.document().createTextNode(value));
        }

        private Element element(Element parent, String tag, String css) {
            if (parent == null) return null;
            Element element = Element.init(this.view.document().createElement(tag));
            element.setClassName(css);
            parent.appendChild(element);
            return element;
        }

        private void link(Element element, String url) {
            if (element == null || url.isEmpty()) return;
            element.setAttribute("role", "link");
            element.setAttribute("title", url);
            element.setClassName("markdown_a markdown_link");
            element.addEventListener("click", event -> {
                event.preventDefault();
                event.stopPropagation();
                if (this.view.interactive()) AssistantPageScreen.openLink(url);
            });
        }

        private void line() {
            if (!this.text.isEmpty() && this.text.charAt(this.text.length() - 1) != '\n') this.text.append('\n');
        }
    }
}
