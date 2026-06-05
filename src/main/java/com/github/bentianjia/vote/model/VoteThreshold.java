package com.github.bentianjia.vote.model;

public final class VoteThreshold {
    private final Mode mode;
    private final int numerator;
    private final int denominator;
    private final int count;

    private VoteThreshold(Mode mode, int numerator, int denominator, int count) {
        this.mode = mode;
        this.numerator = numerator;
        this.denominator = denominator;
        this.count = count;
    }

    public static VoteThreshold all() {
        return new VoteThreshold(Mode.ALL, 1, 1, 0);
    }

    public static VoteThreshold fraction(int numerator, int denominator) {
        if (numerator <= 0 || denominator <= 0) {
            throw new IllegalArgumentException("分子和分母必须大于 0。");
        }
        if (numerator >= denominator) {
            throw new IllegalArgumentException("比例必须是真分数，例如 1/2、2/3；全部通过请写 all。");
        }
        return new VoteThreshold(Mode.FRACTION, numerator, denominator, 0);
    }

    public static VoteThreshold count(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("固定票数必须大于 0。");
        }
        return new VoteThreshold(Mode.COUNT, 1, 1, count);
    }

    public static VoteThreshold parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("缺少通过条件。");
        }
        String value = raw.trim().toLowerCase();
        if ("all".equals(value)) {
            return all();
        }
        if (value.matches("\\d+[a-z]+")) {
            int split = 0;
            while (split < value.length() && Character.isDigit(value.charAt(split))) {
                split++;
            }
            try {
                return count(Integer.parseInt(value.substring(0, split)));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("固定票数格式示例：10p。");
            }
        }
        int slash = value.indexOf('/');
        if (slash <= 0 || slash != value.lastIndexOf('/') || slash == value.length() - 1) {
            throw new IllegalArgumentException("通过条件必须写成真分数 1/2、固定票数 10p，或 all。");
        }
        try {
            int numerator = Integer.parseInt(value.substring(0, slash));
            int denominator = Integer.parseInt(value.substring(slash + 1));
            return fraction(numerator, denominator);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("比例只能包含整数分子和分母，例如 1/2。");
        }
    }

    public int requiredVotes(int realOnlinePlayers) {
        if (mode == Mode.COUNT) {
            return count;
        }
        if (realOnlinePlayers <= 0) {
            return 1;
        }
        return switch (mode) {
            case ALL -> realOnlinePlayers;
            case COUNT -> count;
            case FRACTION -> Math.max(1, (int) Math.ceil(realOnlinePlayers * (numerator / (double) denominator)));
        };
    }

    public String mode() {
        return mode.name();
    }

    public boolean isAll() {
        return mode == Mode.ALL;
    }

    public boolean isCount() {
        return mode == Mode.COUNT;
    }

    public boolean isFraction() {
        return mode == Mode.FRACTION;
    }

    public int count() {
        return count;
    }

    public int numerator() {
        return numerator;
    }

    public int denominator() {
        return denominator;
    }

    public String display() {
        return switch (mode) {
            case ALL -> "all";
            case COUNT -> count + "p";
            case FRACTION -> numerator + "/" + denominator;
        };
    }

    private enum Mode {
        ALL,
        FRACTION,
        COUNT
    }
}

