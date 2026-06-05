package com.github.bentianjia.vote.util;

import org.bukkit.ChatColor;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class Text {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private Text() {
    }

    public static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static String prefix() {
        return color("&8[&bVote&8] &r");
    }

    public static List<String> wrapLore(String text, int maxChars, String colorPrefix) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            lines.add(color(colorPrefix + "无"));
            return lines;
        }
        String normalized = text.replace("\r", "");
        for (String paragraph : normalized.split("\n")) {
            String rest = paragraph.trim();
            if (rest.isEmpty()) {
                lines.add("");
                continue;
            }
            while (rest.length() > maxChars) {
                lines.add(color(colorPrefix + rest.substring(0, maxChars)));
                rest = rest.substring(maxChars);
            }
            lines.add(color(colorPrefix + rest));
        }
        return lines;
    }

    public static String formatMillis(long millis) {
        return DATE_FORMATTER.format(Instant.ofEpochMilli(millis));
    }

    public static String formatRemaining(Long targetMillis) {
        if (targetMillis == null) {
            return "永久";
        }
        long seconds = Math.max(0L, Duration.between(Instant.now(), Instant.ofEpochMilli(targetMillis)).getSeconds());
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;
        if (days > 0) {
            return days + "天 " + hours + "小时";
        }
        if (hours > 0) {
            return hours + "小时 " + minutes + "分钟";
        }
        if (minutes > 0) {
            return minutes + "分钟 " + seconds + "秒";
        }
        return seconds + "秒";
    }

    public static String join(String[] args, int startInclusive, int endExclusive) {
        StringBuilder builder = new StringBuilder();
        for (int i = startInclusive; i < endExclusive; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString().trim();
    }
}

