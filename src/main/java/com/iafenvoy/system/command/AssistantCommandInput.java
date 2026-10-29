package com.iafenvoy.system.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;

/**
 * Skip assistant prefixes without changing argument text or Brigadier's completion replacement offsets.
 */
public record AssistantCommandInput(String text, int start, boolean explicit) {
    public static AssistantCommandInput of(String text) {
        StringReader reader = new StringReader(text);
        reader.skipWhitespace();
        boolean explicit = reader.canRead() && reader.peek() == '/';
        if (explicit) {
            reader.skip();
            int start = reader.getCursor();
            while (reader.canRead() && !Character.isWhitespace(reader.peek())) reader.skip();
            if (text.substring(start, reader.getCursor()).equalsIgnoreCase("system")) {
                reader.skipWhitespace();
                if (reader.canRead() && reader.peek() == '/') reader.skip();
            } else reader.setCursor(start);
        }
        return new AssistantCommandInput(text, reader.getCursor(), explicit);
    }

    public StringReader reader() {
        StringReader reader = new StringReader(this.text);
        reader.setCursor(this.start);
        return reader;
    }

    public StringReader reader(CommandNode<?> root) {
        StringBuilder normalized = new StringBuilder(this.text);
        StringReader cursor = this.reader();
        CommandNode<?> node = root;
        // Preserve case-insensitive literal aliases without lowercasing identifiers, names or free text.
        while (cursor.canRead()) {
            int start = cursor.getCursor();
            while (cursor.canRead() && !Character.isWhitespace(cursor.peek())) cursor.skip();
            String word = this.text.substring(start, cursor.getCursor());
            var literal = node.getChildren().stream().filter(child -> child instanceof LiteralCommandNode<?>
                    && child.getName().equalsIgnoreCase(word) && child.getName().length() == word.length()).findFirst();
            if (literal.isEmpty()) break;
            node = literal.get();
            normalized.replace(start, cursor.getCursor(), node.getName());
            while (cursor.canRead() && Character.isWhitespace(cursor.peek())) {
                normalized.setCharAt(cursor.getCursor(), ' ');
                cursor.skip();
            }
        }
        StringReader reader = new StringReader(normalized.toString());
        reader.setCursor(this.start);
        return reader;
    }

    public String name() {
        int end = this.start;
        while (end < this.text.length() && !Character.isWhitespace(this.text.charAt(end))) end++;
        return this.text.substring(this.start, end);
    }
}
