package com.spellarchives.util;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure GUI/text helper utilities. All methods are stateless and side-effect free,
 * but may depend on the provided FontRenderer and localized I18n strings.
 * Safe to use client-side.
 */
public final class TextUtils {
    private TextUtils() {}

    /**
     * Formats a count into a compact string (e.g., 1.2k, 3M).
     * @param n The count to format
     * @return A compact string representation of the count
     */
    public static String formatCompactCount(long n) {
        if (n < 1000) return Long.toString(n);

        int unitIndex = -1;
        final String[] units = {
            I18n.format("numunit.k"),
            I18n.format("numunit.M"),
            I18n.format("numunit.B"),
            I18n.format("numunit.T")
        };

        double value = n;
        while (value >= 1000 && unitIndex + 1 < units.length) {
            value /= 1000.0;
            unitIndex++;
        }

        String fmt = (value < 100.0)
            ? String.format(Locale.ROOT, "%.1f", value)
            : String.format(Locale.ROOT, "%.0f", value);
        if (fmt.endsWith(".0")) fmt = fmt.substring(0, fmt.length() - 2);

        return fmt + units[unitIndex];
    }

    /**
     * Trims a string to fit within a maximum pixel width, appending an ellipsis if trimmed.
     *
     * @param fr The FontRenderer to use for measuring text width
     * @param text The text to trim
     * @param maxW The maximum width in pixels
     * @return The trimmed text with ellipsis if necessary
     */
    public static String trimToWidth(FontRenderer fr, String text, int maxW) {
        if (text == null) return "";
        if (fr.getStringWidth(text) <= maxW) return text;

        String s = text;
        while (s.length() > 0 && fr.getStringWidth(s + "…") > maxW) {
            s = s.substring(0, s.length() - 1);
        }

        return s + "…";
    }

    /**
     * Formats time in ticks to a human-readable string.
     *
     * @param ticks Time in ticks
     * @return A human-readable string representing the time (e.g., "3h", "3.5s", "120t", "instant")
    */
    public static String formatTimeTicks(int ticks) {
        if (ticks <= 0) return I18n.format("gui.spellarchives.instant");

        double value = ticks;
        final int[] timeUnits = {20, 60, 60, 24, Integer.MAX_VALUE};
        final String[] timeUnitLabels = {
            I18n.format("timeunit.t"),
            I18n.format("timeunit.s"),
            I18n.format("timeunit.m"),
            I18n.format("timeunit.h"),
            I18n.format("timeunit.d")
        };

        int i = 0;
        for (; i < timeUnits.length; i++) {
            int unitSize = timeUnits[i];
            if (value < unitSize) break;
            value /= unitSize;
        }

        String unit = timeUnitLabels[i];
        String s = String.format(Locale.ROOT, "%.1f%s", value, unit);
        if (s.endsWith(".0" + unit)) s = s.substring(0, s.length() - 2 - unit.length()) + unit;

        return s;
    }

    /**
     * Darkens a color by the given factor.
     *
     * @param rgb the original color in 0xRRGGBB format
     * @param factor the darkening factor (0.0 = black, 1.0 = original color)
     * @return the darkened color in 0xRRGGBB format
     */
    public static int darkenColor(int rgb, float factor) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        r = Math.max(0, Math.min(255, (int) (r * factor)));
        g = Math.max(0, Math.min(255, (int) (g * factor)));
        b = Math.max(0, Math.min(255, (int) (b * factor)));

        return (r << 16) | (g << 8) | b;
    }

    /**
     * Extracts an RGB color from a Style object, with a fallback if not present.
     *
     * @param style The Style object to extract the color from
     * @param fallbackRgb The fallback RGB color if none is found
     * @return The extracted RGB color or the fallback
     */
    public static int rgbFromStyle(Style style, int fallbackRgb) {
        if (style == null) return fallbackRgb;

        TextFormatting tf = style.getColor();
        if (tf == null) return fallbackRgb;

        switch (tf) {
            case BLACK: return 0x000000;
            case DARK_BLUE: return 0x0000AA;
            case DARK_GREEN: return 0x00AA00;
            case DARK_AQUA: return 0x00AAAA;
            case DARK_RED: return 0xAA0000;
            case DARK_PURPLE: return 0xAA00AA;
            case GOLD: return 0xFFAA00;
            case GRAY: return 0xAAAAAA;
            case DARK_GRAY: return 0x555555;
            case BLUE: return 0x5555FF;
            case GREEN: return 0x55FF55;
            case AQUA: return 0x55FFFF;
            case RED: return 0xFF5555;
            case LIGHT_PURPLE: return 0xFF55FF;
            case YELLOW: return 0xFFFF55;
            case WHITE: return 0xFFFFFF;
            default: return fallbackRgb;
        }
    }

    /**
     * Sanitize a string before passing to ICU/line-break routines.
     * Removes embedded NULs, replaces unpaired surrogates with the replacement char,
     * strips most ISO control characters (except newline/tab/CR), and truncates
     * the string to a reasonable maximum length to avoid internal iterator issues.
     *
     * @param s The input string
     * @return The sanitized string
     */
    public static String sanitizeForBreakIterator(String s) {
        if (s == null || s.isEmpty()) return "";

        final int MAX_LEN = 4096;
        StringBuilder sb = new StringBuilder(Math.min(s.length(), MAX_LEN));

        int len = s.length();
        for (int i = 0; i < len && sb.length() < MAX_LEN;) {
            char c = s.charAt(i);
            if (c == '\u0000') { i++; continue; }

            if (Character.isHighSurrogate(c)) {
                if (i + 1 < len && Character.isLowSurrogate(s.charAt(i + 1))) {
                    sb.append(c).append(s.charAt(i + 1));
                    i += 2;
                    continue;
                } else { sb.append('\uFFFD'); i++; continue; }
            }

            if (Character.isLowSurrogate(c)) { sb.append('\uFFFD'); i++; continue; }
            if (c < 0x20 && c != '\n' && c != '\r' && c != '\t') { i++; continue; }

            sb.append(c);
            i++;
        }

        return sb.toString();
    }

    /**
     * Simple word-wrap that uses the provided FontRenderer to measure widths.
     * This avoids ICU/BreakIterator usage which can sometimes fail on malformed input.
     *
     * @param fr The FontRenderer to use for measuring text width
     * @param text The text to wrap
     * @param maxWidth The maximum width in pixels for each line
     * @return A list of wrapped lines
     */
    public static List<String> wrapTextToWidth(FontRenderer fr, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty() || maxWidth <= 0) return lines;

        String[] paragraphs = text.split("\\r?\\n");
        for (String para : paragraphs) {
            String p = para.trim();
            if (p.isEmpty()) { lines.add(""); continue; }

            String[] words = p.split(" ");
            StringBuilder cur = new StringBuilder();

            for (String w : words) {
                if (w.isEmpty()) continue;  // Skip multiple spaces

                int wordWidth = fr.getStringWidth(w);
                if (wordWidth <= maxWidth) {
                    // Word fits on its own.
                    if (cur.length() == 0) {
                        cur.append(w);
                    } else {
                        String test = cur.toString() + " " + w;
                        if (fr.getStringWidth(test) <= maxWidth) {
                            cur.append(" ").append(w);
                        } else {
                            lines.add(cur.toString());
                            cur.setLength(0);
                            cur.append(w);
                        }
                    }
                } else {
                    // Word itself is too long: split into chunks that fit maxWidth.
                    int start = 0;
                    int wlen = w.length();

                    while (start < wlen) {
                        if (cur.length() > 0) {
                            // Try to append as many chars from the word to the current line as will fit.
                            int end = start;
                            for (int e = start; e < wlen; e++) {
                                String test = cur.toString() + w.substring(start, e + 1);
                                if (fr.getStringWidth(test) <= maxWidth) {
                                    end = e + 1;
                                } else break;
                            }

                            if (end == start) {
                                // Can't fit even a single additional char on the current line: flush it.
                                lines.add(cur.toString());
                                cur.setLength(0);
                                continue;
                            }

                            cur.append(w.substring(start, end));
                            start = end;

                            // After filling the current line, flush it so further chunks start on a new line.
                            lines.add(cur.toString());
                            cur.setLength(0);
                        } else {
                            // Current line is empty: build the largest chunk starting at 'start' that fits.
                            int end = start + 1;
                            for (int e = start + 1; e <= wlen; e++) {
                                String piece = w.substring(start, e);
                                if (fr.getStringWidth(piece) <= maxWidth) {
                                    end = e;
                                } else break;
                            }

                            // Ensure progress: if a single char is wider than maxWidth, force one char.
                            if (end == start) end = start + 1;

                            String piece = w.substring(start, end);
                            cur.append(piece);
                            start = end;

                            // If the chunk filled the line exactly (or can't accept more), flush it now.
                            if (fr.getStringWidth(cur.toString()) >= maxWidth) {
                                lines.add(cur.toString());
                                cur.setLength(0);
                            }
                        }
                    }
                }
            }

            if (cur.length() > 0) lines.add(cur.toString());
        }

        return lines;
    }
}
