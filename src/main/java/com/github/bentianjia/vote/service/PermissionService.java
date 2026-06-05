package com.github.bentianjia.vote.service;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PermissionService {
    private final JavaPlugin plugin;

    public PermissionService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean canAdmin(CommandSender sender) {
        if (sender instanceof ConsoleCommandSender) {
            return true;
        }
        if (sender.hasPermission("vote.admin")) {
            return true;
        }
        if (sender instanceof Player player && player.isOp()) {
            int minLevel = plugin.getConfig().getInt("vote.min-admin-op-level", 2);
            OptionalInt level = resolveOpLevel(player);
            return level.isEmpty() || level.getAsInt() >= minLevel;
        }
        return false;
    }

    public boolean canVote(CommandSender sender) {
        return canAdmin(sender) || sender.hasPermission("vote.vote");
    }

    public boolean canAccept(CommandSender sender) {
        return canAdmin(sender) || (canVote(sender) && sender.hasPermission("vote.accept"));
    }

    public boolean canReject(CommandSender sender) {
        return canAdmin(sender) || (canVote(sender) && sender.hasPermission("vote.reject"));
    }

    private OptionalInt resolveOpLevel(Player player) {
        File opsFile = new File(plugin.getServer().getWorldContainer(), "ops.json");
        if (!opsFile.isFile()) {
            return OptionalInt.empty();
        }
        try {
            String content = Files.readString(opsFile.toPath(), StandardCharsets.UTF_8);
            OptionalInt byUuid = findLevel(content, "uuid", player.getUniqueId().toString());
            if (byUuid.isPresent()) {
                return byUuid;
            }
            return findLevel(content, "name", player.getName());
        } catch (IOException ex) {
            return OptionalInt.empty();
        }
    }

    private OptionalInt findLevel(String content, String field, String value) {
        String quotedField = Pattern.quote(field);
        String quotedValue = Pattern.quote(value);
        Pattern objectPattern = Pattern.compile("\\{[^{}]*\"" + quotedField + "\"\\s*:\\s*\"" + quotedValue
                + "\"[^{}]*}", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher objectMatcher = objectPattern.matcher(content);
        if (!objectMatcher.find()) {
            return OptionalInt.empty();
        }
        Matcher levelMatcher = Pattern.compile("\"level\"\\s*:\\s*(\\d+)").matcher(objectMatcher.group());
        if (!levelMatcher.find()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Integer.parseInt(levelMatcher.group(1)));
    }
}
