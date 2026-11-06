package net.minecraft;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Compatibility shim for Minecraft's ChatFormatting: the legacy section-sign
 * codes.
 *
 * <p>This is the 26.3 surface: the enum carries a private {@code code} and a
 * prebuilt {@link #toString()}, and exposes only {@link #PREFIX_CODE},
 * {@link #getByCode(char)} and {@link #stripFormatting(String)} beyond the
 * enum's own members. (The name/id/colour members existed on 26.1.x and were
 * removed by vanilla before 26.3; there is nothing sensible to shim them
 * against.) Colour resolution now lives in
 * {@code net.minecraft.network.chat.TextColor}.
 */
public enum ChatFormatting {
    BLACK('0'),
    DARK_BLUE('1'),
    DARK_GREEN('2'),
    DARK_AQUA('3'),
    DARK_RED('4'),
    DARK_PURPLE('5'),
    GOLD('6'),
    GRAY('7'),
    DARK_GRAY('8'),
    BLUE('9'),
    GREEN('a'),
    AQUA('b'),
    RED('c'),
    LIGHT_PURPLE('d'),
    YELLOW('e'),
    WHITE('f'),
    OBFUSCATED('k'),
    BOLD('l'),
    STRIKETHROUGH('m'),
    UNDERLINE('n'),
    ITALIC('o'),
    RESET('r');

    /** Vanilla's section-sign prefix ({@code '\u00A7'}). */
    public static final char PREFIX_CODE = '\u00A7';
    private static final Pattern STRIP_PATTERN = Pattern.compile("(?i)\\u00A7[0-9A-FK-OR]");

    private final char code;
    private final String toString;

    ChatFormatting(char code) {
        this.code = code;
        this.toString = PREFIX_CODE + String.valueOf(code);
    }

    @Override
    public String toString() {
        return this.toString;
    }

    /**
     * @return the formatting with this code, or {@code null} for an unknown
     *         code (vanilla returns null too)
     */
    public static ChatFormatting getByCode(char code) {
        for (ChatFormatting value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }

    /**
     * Strips every recognised {@code \u00A7X} sequence. Vanilla compiles the
     * same pattern; the shim's null handling (returns null) is its own and is
     * not compared by the oracle.
     */
    public static String stripFormatting(String text) {
        return text == null ? null : STRIP_PATTERN.matcher(text).replaceAll("");
    }
}
