package com.github.bentianjia.vote.util;

import com.github.bentianjia.vote.model.VoteThreshold;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CommandParsers {
    private CommandParsers() {
    }

    public static CreateVoteInput parseCreate(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException(createUsage());
        }

        Map<Field, String> fields = parseFields(args, 1);
        String summary = required(fields, Field.SUMMARY, "summary");
        String details = required(fields, Field.DETAILS, "details");
        String amount = required(fields, Field.AMOUNT, "amount");

        List<String> commands = parseCommandList(fields.get(Field.COMMAND));
        Duration timeout = null;
        if (fields.containsKey(Field.TIMEOUT)) {
            timeout = parseOptionalTimeout(fields.get(Field.TIMEOUT));
        }

        return new CreateVoteInput(summary, details, commands, timeout, VoteThreshold.parse(amount));
    }

    public static EditVoteInput parseEdit(String[] args) {
        if (args.length < 3) {
            throw new IllegalArgumentException(editUsage());
        }
        int firstField = firstFieldIndex(args, 1);
        if (firstField < 0) {
            throw new IllegalArgumentException("请指定要编辑的字段，例如 summary:新标题、command:none。");
        }
        String target = Text.join(args, 1, firstField);
        if (target.isBlank()) {
            throw new IllegalArgumentException("缺少要编辑的标题或 ID。");
        }

        Map<Field, String> fields = parseFields(args, firstField);
        String summary = fields.get(Field.SUMMARY);
        String details = fields.get(Field.DETAILS);
        List<String> commands = fields.containsKey(Field.COMMAND) ? parseCommandList(fields.get(Field.COMMAND)) : null;
        boolean timeoutProvided = fields.containsKey(Field.TIMEOUT);
        Duration timeout = timeoutProvided ? parseOptionalTimeout(fields.get(Field.TIMEOUT)) : null;
        VoteThreshold threshold = fields.containsKey(Field.AMOUNT) ? VoteThreshold.parse(fields.get(Field.AMOUNT)) : null;

        return new EditVoteInput(target, summary, details, commands, timeoutProvided, timeout, threshold);
    }

    public static List<String> parseCommandList(String commandText) {
        if (commandText == null || commandText.isBlank() || commandText.equalsIgnoreCase("none")
                || commandText.equalsIgnoreCase("无")) {
            return List.of();
        }
        List<String> commands = new ArrayList<>();
        for (String part : commandText.split(";")) {
            String command = part.trim();
            while (command.startsWith("/")) {
                command = command.substring(1);
            }
            if (!command.isBlank()) {
                commands.add(command);
            }
        }
        return commands;
    }

    public static String createUsage() {
        return "用法：/vote create summary:<标题> details:<详细内容> [command:<命令;命令>] [timeout:<时间|none>] amount:<比例|票数p|all>";
    }

    public static String editUsage() {
        return "用法：/vote edit <标题或ID> [summary:<新标题>] [details:<新内容>] [command:<命令;命令|none>] [timeout:<时间|none>] [amount:<比例|票数p|all>]";
    }

    private static Map<Field, String> parseFields(String[] args, int start) {
        Map<Field, StringBuilder> builders = new LinkedHashMap<>();
        Field current = null;
        boolean colonMode = usesColonMarkers(args, start);

        for (int i = start; i < args.length; i++) {
            Marker marker = marker(args[i], colonMode);
            if (marker != null) {
                current = marker.field();
                if (builders.containsKey(current)) {
                    throw new IllegalArgumentException("字段重复：" + current.primaryName());
                }
                builders.put(current, new StringBuilder());
                if (!marker.inlineValue().isBlank()) {
                    builders.get(current).append(marker.inlineValue());
                }
                continue;
            }

            if (current == null) {
                throw new IllegalArgumentException("缺少字段引导。请使用 summary:、details:、command:、timeout:、amount:。");
            }
            StringBuilder builder = builders.get(current);
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }

        Map<Field, String> result = new LinkedHashMap<>();
        for (Map.Entry<Field, StringBuilder> entry : builders.entrySet()) {
            String value = entry.getValue().toString().trim();
            if (value.isBlank()) {
                throw new IllegalArgumentException(entry.getKey().primaryName() + " 后面的内容不能为空。");
            }
            result.put(entry.getKey(), value);
        }
        return result;
    }

    private static String required(Map<Field, String> fields, Field field, String label) {
        String value = fields.get(field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少必填字段 " + label + "。");
        }
        return value;
    }

    private static Duration parseOptionalTimeout(String value) {
        if (value.equalsIgnoreCase("none") || value.equalsIgnoreCase("无")) {
            return null;
        }
        return DurationParser.parse(value);
    }

    private static int firstFieldIndex(String[] args, int start) {
        boolean colonMode = usesColonMarkers(args, start);
        for (int i = start; i < args.length; i++) {
            if (marker(args[i], colonMode) != null) {
                return i;
            }
        }
        return -1;
    }

    private static boolean usesColonMarkers(String[] args, int start) {
        for (int i = start; i < args.length; i++) {
            String token = args[i];
            if (token == null) {
                continue;
            }
            int colon = token.indexOf(':');
            if (colon > 0 && Field.from(token.substring(0, colon).toLowerCase(Locale.ROOT)) != null) {
                return true;
            }
        }
        return false;
    }

    private static Marker marker(String token, boolean colonMode) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String normalized = token.toLowerCase(Locale.ROOT);
        int colon = normalized.indexOf(':');
        if (colonMode && colon < 0) {
            return null;
        }
        if (!colonMode && colon >= 0) {
            return null;
        }
        String key = colon >= 0 ? normalized.substring(0, colon) : normalized;
        Field field = Field.from(key);
        if (field == null) {
            return null;
        }
        String inlineValue = colon >= 0 ? token.substring(colon + 1) : "";
        return new Marker(field, inlineValue);
    }

    public record CreateVoteInput(String summary, String details, List<String> commands, Duration timeout,
                                  VoteThreshold threshold) {
    }

    public record EditVoteInput(String target, String summary, String details, List<String> commands,
                                boolean timeoutProvided, Duration timeout, VoteThreshold threshold) {
        public boolean isEmpty() {
            return summary == null && details == null && commands == null && !timeoutProvided && threshold == null;
        }
    }

    private record Marker(Field field, String inlineValue) {
    }

    private enum Field {
        SUMMARY("summary", "title"),
        DETAILS("details", "detail"),
        COMMAND("command", "commands", "cmd"),
        TIMEOUT("timeout", "time"),
        AMOUNT("amount", "threshold", "count", "required", "pass");

        private final String[] aliases;

        Field(String... aliases) {
            this.aliases = aliases;
        }

        private String primaryName() {
            return aliases[0];
        }

        private static Field from(String key) {
            for (Field field : values()) {
                for (String alias : field.aliases) {
                    if (alias.equals(key)) {
                        return field;
                    }
                }
            }
            return null;
        }
    }
}
