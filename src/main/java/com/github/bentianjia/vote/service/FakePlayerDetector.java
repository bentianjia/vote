package com.github.bentianjia.vote.service;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class FakePlayerDetector {
    private final JavaPlugin plugin;
    private final List<Pattern> namePatterns = new ArrayList<>();

    public FakePlayerDetector(JavaPlugin plugin) {
        this.plugin = plugin;
        for (String pattern : plugin.getConfig().getStringList("fake-player.name-patterns")) {
            try {
                namePatterns.add(Pattern.compile(pattern, Pattern.CASE_INSENSITIVE));
            } catch (PatternSyntaxException ex) {
                plugin.getLogger().warning("忽略无效假人名称正则：" + pattern);
            }
        }
    }

    public boolean isFake(Player player) {
        if (player.hasPermission("vote.fake")) {
            return true;
        }
        for (String key : plugin.getConfig().getStringList("fake-player.metadata-keys")) {
            if (player.hasMetadata(key)) {
                return true;
            }
        }
        for (Pattern pattern : namePatterns) {
            if (pattern.matcher(player.getName()).matches()) {
                return true;
            }
        }
        String className = player.getClass().getName().toLowerCase(Locale.ROOT);
        return className.contains("fake") || className.contains("npc");
    }
}

