package fr.craftpick.permsability.util;

import org.bukkit.ChatColor;

public final class Text {
    private Text() {}

    public static String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }
}
