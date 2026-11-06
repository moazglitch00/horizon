package net.minecraft.network.chat;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import net.minecraft.ChatFormatting;

/**
 * Compatibility shim for Minecraft's TextColor (26.3 surface).
 *
 * <p>The 16 legacy colours are exposed as the class's static constants,
 * exactly as 26.3 does, and their values, names, {@code serialize()} strings
 * and {@code hashCode()}s were read from a real 26.3 jar.
 *
 * <ul>
 *   <li>{@code fromRgb} masks the argument to 24 bits and carries no name;</li>
 *   <li>{@code fromLegacyFormat} carries the legacy name and returns
 *   {@code null} for non-colour formats (vanilla returns null too, e.g.
 *   {@code BOLD} and {@code RESET});</li>
 *   <li>{@code serialize}/{@code toString} return the name, or
 *   {@code "#%06X"} (uppercase) for unnamed colours;</li>
 *   <li>{@code equals} compares the value only (named and hex colours with
 *   the same value are equal);</li>
 *   <li>{@code hashCode} is {@code 31 * value + (name == null ? 0 :
 *   name.hashCode()) + 961}, matching vanilla, including the mismatch with
 *   {@code equals} for unnamed colours.</li>
 * </ul>
 *
 * <p>{@code parseColor} is not ported: it returns Mojang's
 * {@code com.mojang.serialization.DataResult}, which this shim layer does not
 * have, and a throwaway wrapper would be a lie.
 */
public final class TextColor {
    private static final String CUSTOM_COLOR_PREFIX = "#";

    public static final TextColor BLACK = named("black", 0x000000);
    public static final TextColor DARK_BLUE = named("dark_blue", 0x0000AA);
    public static final TextColor DARK_GREEN = named("dark_green", 0x00AA00);
    public static final TextColor DARK_AQUA = named("dark_aqua", 0x00AAAA);
    public static final TextColor DARK_RED = named("dark_red", 0xAA0000);
    public static final TextColor DARK_PURPLE = named("dark_purple", 0xAA00AA);
    public static final TextColor GOLD = named("gold", 0xFFAA00);
    public static final TextColor GRAY = named("gray", 0xAAAAAA);
    public static final TextColor DARK_GRAY = named("dark_gray", 0x555555);
    public static final TextColor BLUE = named("blue", 0x5555FF);
    public static final TextColor GREEN = named("green", 0x55FF55);
    public static final TextColor AQUA = named("aqua", 0x55FFFF);
    public static final TextColor RED = named("red", 0xFF5555);
    public static final TextColor LIGHT_PURPLE = named("light_purple", 0xFF55FF);
    public static final TextColor YELLOW = named("yellow", 0xFFFF55);
    public static final TextColor WHITE = named("white", 0xFFFFFF);

    private static final Map<ChatFormatting, TextColor> LEGACY = createLegacyMap();

    private final int value;
    private final String name;

    private static TextColor named(String name, int value) {
        return new TextColor(value, name);
    }

    private TextColor(int value, String name) {
        this.value = value;
        this.name = name;
    }

    public static TextColor fromRgb(int rgb) {
        return new TextColor(rgb & 0xFFFFFF, null);
    }

    /**
     * @param format the legacy formatting to reference; vanilla throws
     *         {@code NullPointerException} for null (it calls
     *         {@code format.ordinal()} unconditionally) and so do we
     * @return the colour, or {@code null} for non-colour formats
     *         (vanilla returns null too, e.g. for {@code BOLD} and {@code RESET})
     */
    public static TextColor fromLegacyFormat(ChatFormatting format) {
        if (format == null) {
            throw new NullPointerException("format");
        }
        return LEGACY.get(format);
    }

    public int getValue() {
        return this.value;
    }

    public String serialize() {
        return this.name != null ? this.name : formatValue();
    }

    public String toString() {
        return this.serialize();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof TextColor that && this.value == that.value;
    }

    @Override
    public int hashCode() {
        int nameHash = this.name == null ? 0 : this.name.hashCode();
        return 31 * this.value + nameHash + 961;
    }

    private String formatValue() {
        return String.format(Locale.ROOT, "#%06X", this.value);
    }

    private static Map<ChatFormatting, TextColor> createLegacyMap() {
        Map<ChatFormatting, TextColor> map = new EnumMap<>(ChatFormatting.class);
        map.put(ChatFormatting.BLACK, BLACK);
        map.put(ChatFormatting.DARK_BLUE, DARK_BLUE);
        map.put(ChatFormatting.DARK_GREEN, DARK_GREEN);
        map.put(ChatFormatting.DARK_AQUA, DARK_AQUA);
        map.put(ChatFormatting.DARK_RED, DARK_RED);
        map.put(ChatFormatting.DARK_PURPLE, DARK_PURPLE);
        map.put(ChatFormatting.GOLD, GOLD);
        map.put(ChatFormatting.GRAY, GRAY);
        map.put(ChatFormatting.DARK_GRAY, DARK_GRAY);
        map.put(ChatFormatting.BLUE, BLUE);
        map.put(ChatFormatting.GREEN, GREEN);
        map.put(ChatFormatting.AQUA, AQUA);
        map.put(ChatFormatting.RED, RED);
        map.put(ChatFormatting.LIGHT_PURPLE, LIGHT_PURPLE);
        map.put(ChatFormatting.YELLOW, YELLOW);
        map.put(ChatFormatting.WHITE, WHITE);
        return Collections.unmodifiableMap(map);
    }
}
