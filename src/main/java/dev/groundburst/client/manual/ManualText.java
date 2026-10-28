package dev.groundburst.client.manual;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * The manual's text markup: paragraphs split by a blank line, a single newline breaks the line, {@code **bold**},
 * {@code *italic*}, lines starting {@code - } become bullets, and {@code [label](chapter/entry)} links to an entry.
 * A link's target rides on the text style's insertion, so a click finds it with the font's splitter.
 */
public final class ManualText {
    public static final int INK = 0xFF2B2A26;
    public static final int LINK = 0xFF1F4E8C;
    private static final String BULLET = "• ";

    private ManualText() {}

    /** Lays {@code text} out to {@code width}; a blank line becomes a half-height gap marker ({@code null}). */
    public static List<@Nullable FormattedCharSequence> lines(Font font, String text, int width) {
        List<@Nullable FormattedCharSequence> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        String[] paragraphs = text.strip().split("\\n\\s*\\n");
        for (int p = 0; p < paragraphs.length; p++) {
            if (p > 0) {
                out.add(null);
            }
            for (String line : paragraphs[p].split("\\n")) {
                boolean bullet = line.startsWith("- ");
                Component c = parse(bullet ? line.substring(2) : line);
                if (bullet) {
                    // Hanging indent: the first line carries the bullet, the rest line up under its text.
                    int indent = font.width(BULLET);
                    List<FormattedCharSequence> wrapped = font.split(c, width - indent);
                    for (int i = 0; i < wrapped.size(); i++) {
                        FormattedCharSequence prefix = Component.literal(i == 0 ? BULLET : "").getVisualOrderText();
                        FormattedCharSequence body = wrapped.get(i);
                        out.add(i == 0 ? FormattedCharSequence.composite(prefix, body) : indent(body, indent, font));
                    }
                } else {
                    out.addAll(font.split(c, width));
                }
            }
        }
        return out;
    }

    /** Pads a wrapped bullet line with spaces so it lines up under the text after the bullet. */
    private static FormattedCharSequence indent(FormattedCharSequence body, int indent, Font font) {
        int spaces = Math.max(1, Math.round(indent / (float) font.width(" ")));
        return FormattedCharSequence.composite(Component.literal(" ".repeat(spaces)).getVisualOrderText(), body);
    }

    /** One line's inline markup to a styled component. */
    public static Component parse(String s) {
        MutableComponent out = Component.empty();
        StringBuilder run = new StringBuilder();
        boolean bold = false, italic = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '*' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                flush(out, run, bold, italic, null);
                bold = !bold;
                i++;
            } else if (ch == '*') {
                flush(out, run, bold, italic, null);
                italic = !italic;
            } else if (ch == '[') {
                int close = s.indexOf(']', i);
                if (close > i && close + 1 < s.length() && s.charAt(close + 1) == '(') {
                    int end = s.indexOf(')', close);
                    if (end > close) {
                        flush(out, run, bold, italic, null);
                        run.append(s, i + 1, close);
                        flush(out, run, bold, italic, s.substring(close + 2, end));
                        i = end;
                        continue;
                    }
                }
                run.append(ch);
            } else {
                run.append(ch);
            }
        }
        flush(out, run, bold, italic, null);
        return out;
    }

    private static void flush(MutableComponent out, StringBuilder run, boolean bold, boolean italic, @Nullable String link) {
        if (run.isEmpty()) {
            return;
        }
        Style style = Style.EMPTY.withBold(bold).withItalic(italic);
        if (link != null) {
            style = style.withColor(LINK & 0xFFFFFF).withUnderlined(true).withInsertion(link);
        }
        out.append(Component.literal(run.toString()).withStyle(style));
        run.setLength(0);
    }

    /** The link target under {@code x} pixels into {@code line}, if any. */
    public static @Nullable String linkAt(Font font, @Nullable FormattedCharSequence line, int x) {
        if (line == null || x < 0) {
            return null;
        }
        // Walk the line's characters, adding up their widths, until passing x.
        String[] found = {null};
        float[] at = {0};
        line.accept((index, style, codepoint) -> {
            float w = font.width(FormattedCharSequence.forward(new String(Character.toChars(codepoint)), style));
            if (x < at[0] + w) {
                found[0] = style.getInsertion();
                return false;
            }
            at[0] += w;
            return true;
        });
        return found[0];
    }
}
