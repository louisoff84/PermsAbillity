package fr.craftpick.permsability.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {
    private static final Pattern TOKEN = Pattern.compile("(\\d+)([smhdw])", Pattern.CASE_INSENSITIVE);

    private DurationParser() {}

    public static long parseExpiry(String input) {
        if (input == null || input.trim().isEmpty() || input.equalsIgnoreCase("permanent") || input.equalsIgnoreCase("perm")) {
            return 0L;
        }
        String value = input.toLowerCase(Locale.ROOT).replace(" ", "");
        Matcher matcher = TOKEN.matcher(value);
        long millis = 0L;
        int consumed = 0;
        while (matcher.find()) {
            if (matcher.start() != consumed) throw new IllegalArgumentException("Invalid duration");
            long amount = Long.parseLong(matcher.group(1));
            char unit = matcher.group(2).charAt(0);
            if (unit == 's') millis += amount * 1000L;
            if (unit == 'm') millis += amount * 60_000L;
            if (unit == 'h') millis += amount * 3_600_000L;
            if (unit == 'd') millis += amount * 86_400_000L;
            if (unit == 'w') millis += amount * 604_800_000L;
            consumed = matcher.end();
        }
        if (consumed != value.length() || millis <= 0L) throw new IllegalArgumentException("Invalid duration");
        return System.currentTimeMillis() + millis;
    }

    public static String describeExpiry(long expiry) {
        if (expiry <= 0L) return "permanent";
        long seconds = Math.max(0L, (expiry - System.currentTimeMillis()) / 1000L);
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h";
        long days = hours / 24;
        return days + "d";
    }
}
