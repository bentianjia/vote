package com.github.bentianjia.vote.util;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {
    private static final Pattern PART = Pattern.compile("(\\d+(?:\\.\\d+)?)(mo|y|d|h|m|s)", Pattern.CASE_INSENSITIVE);

    private DurationParser() {
    }

    public static Duration parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("缺少超时时间。");
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        Matcher matcher = PART.matcher(value);
        int index = 0;
        BigDecimal seconds = BigDecimal.ZERO;
        while (matcher.find()) {
            if (matcher.start() != index) {
                throw new IllegalArgumentException("无法识别超时时间：" + raw);
            }
            BigDecimal number = new BigDecimal(matcher.group(1));
            if (number.signum() <= 0) {
                throw new IllegalArgumentException("超时时间必须大于 0。");
            }
            seconds = seconds.add(number.multiply(BigDecimal.valueOf(unitSeconds(matcher.group(2)))));
            index = matcher.end();
        }
        if (index != value.length() || seconds.signum() <= 0) {
            throw new IllegalArgumentException("超时时间格式示例：30m、1.5h、2d、1mo。");
        }
        long wholeSeconds = seconds.setScale(0, java.math.RoundingMode.CEILING).longValueExact();
        return Duration.ofSeconds(wholeSeconds);
    }

    private static long unitSeconds(String unit) {
        return switch (unit.toLowerCase(Locale.ROOT)) {
            case "y" -> 365L * 24L * 60L * 60L;
            case "mo" -> 30L * 24L * 60L * 60L;
            case "d" -> 24L * 60L * 60L;
            case "h" -> 60L * 60L;
            case "m" -> 60L;
            case "s" -> 1L;
            default -> throw new IllegalArgumentException("未知时间单位：" + unit);
        };
    }
}

