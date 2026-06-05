package com.github.bentianjia.vote.storage;

import com.github.bentianjia.vote.model.VoteChoice;
import com.github.bentianjia.vote.model.VoteRecord;
import com.github.bentianjia.vote.model.VoteStatus;
import com.github.bentianjia.vote.model.VoteThreshold;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class VoteStore {
    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, VoteRecord> votes = new LinkedHashMap<>();

    public VoteStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "votes.yml");
    }

    public void load() {
        votes.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("votes");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            try {
                VoteRecord record = readRecord(id, section);
                votes.put(record.id(), record);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("无法读取投票 " + id + ": " + ex.getMessage());
            }
        }
    }

    public void save() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("无法创建插件数据目录：" + plugin.getDataFolder());
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (VoteRecord record : votes.values()) {
            writeRecord(yaml.createSection("votes." + record.id()), record);
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("保存 votes.yml 失败：" + ex.getMessage());
        }
    }

    public List<VoteRecord> allVotes() {
        List<VoteRecord> list = new ArrayList<>(votes.values());
        list.sort(Comparator.comparing(VoteRecord::isActive).reversed()
                .thenComparing(Comparator.comparingLong(VoteRecord::createdAt).reversed()));
        return list;
    }

    public Optional<VoteRecord> byId(String id) {
        return Optional.ofNullable(votes.get(id));
    }

    public Optional<VoteRecord> find(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        VoteRecord exactId = votes.get(query.trim());
        if (exactId != null) {
            return Optional.of(exactId);
        }
        List<VoteRecord> matches = new ArrayList<>();
        for (VoteRecord record : votes.values()) {
            if (record.id().toLowerCase(Locale.ROOT).startsWith(normalized)
                    || record.summary().equalsIgnoreCase(query.trim())) {
                matches.add(record);
            }
        }
        if (matches.size() == 1) {
            return Optional.of(matches.get(0));
        }
        List<VoteRecord> activeMatches = matches.stream()
                .filter(VoteRecord::isActive)
                .toList();
        if (activeMatches.size() == 1) {
            return Optional.of(activeMatches.get(0));
        }
        return Optional.empty();
    }

    public boolean activeTitleExists(String summary) {
        return activeTitleExists(summary, null);
    }

    public boolean activeTitleExists(String summary, String ignoredId) {
        for (VoteRecord record : votes.values()) {
            if (record.isActive()
                    && !record.id().equals(ignoredId)
                    && record.summary().equalsIgnoreCase(summary)) {
                return true;
            }
        }
        return false;
    }

    public void put(VoteRecord record) {
        votes.put(record.id(), record);
    }

    public void remove(String id) {
        votes.remove(id);
    }

    private VoteRecord readRecord(String id, ConfigurationSection section) {
        String summary = section.getString("summary", "");
        String details = section.getString("details", "");
        List<String> commands = section.getStringList("commands");
        UUID creatorUuid = parseUuid(section.getString("creator.uuid", null));
        String creatorName = section.getString("creator.name", "unknown");
        long createdAt = section.getLong("created-at", System.currentTimeMillis());
        Long expiresAt = section.contains("expires-at") ? section.getLong("expires-at") : null;
        VoteThreshold threshold = readThreshold(section.getConfigurationSection("threshold"));
        VoteStatus status = VoteStatus.valueOf(section.getString("status", VoteStatus.ACTIVE.name()));
        boolean commandsExecuted = section.getBoolean("commands-executed", false);
        Long closedAt = section.contains("closed-at") ? section.getLong("closed-at") : null;

        VoteRecord record = new VoteRecord(id, summary, details, commands, creatorUuid, creatorName, createdAt,
                expiresAt, threshold, status, commandsExecuted, closedAt);
        ConfigurationSection votesSection = section.getConfigurationSection("votes");
        if (votesSection != null) {
            for (String uuidText : votesSection.getKeys(false)) {
                UUID uuid = parseUuid(uuidText);
                if (uuid == null) {
                    continue;
                }
                ConfigurationSection voteSection = votesSection.getConfigurationSection(uuidText);
                if (voteSection == null) {
                    continue;
                }
                VoteChoice choice = VoteChoice.valueOf(voteSection.getString("choice", VoteChoice.OPPOSE.name()));
                String name = voteSection.getString("name", uuidText);
                record.votes().put(uuid, choice);
                record.voterNames().put(uuid, name);
            }
        }
        return record;
    }

    private void writeRecord(ConfigurationSection section, VoteRecord record) {
        section.set("summary", record.summary());
        section.set("details", record.details());
        section.set("commands", record.commands());
        section.set("creator.uuid", record.creatorUuid() == null ? null : record.creatorUuid().toString());
        section.set("creator.name", record.creatorName());
        section.set("created-at", record.createdAt());
        section.set("expires-at", record.expiresAt());
        section.set("threshold.type", record.threshold().mode());
        section.set("threshold.all", record.threshold().isAll());
        section.set("threshold.numerator", record.threshold().numerator());
        section.set("threshold.denominator", record.threshold().denominator());
        section.set("threshold.count", record.threshold().count());
        section.set("status", record.status().name());
        section.set("commands-executed", record.commandsExecuted());
        section.set("closed-at", record.closedAt());
        for (Map.Entry<UUID, VoteChoice> entry : record.votes().entrySet()) {
            String path = "votes." + entry.getKey();
            section.set(path + ".choice", entry.getValue().name());
            section.set(path + ".name", record.voterNames().getOrDefault(entry.getKey(), entry.getKey().toString()));
        }
    }

    private VoteThreshold readThreshold(ConfigurationSection section) {
        if (section == null || section.getBoolean("all", false)) {
            return VoteThreshold.all();
        }
        String type = section.getString("type", "");
        if ("COUNT".equalsIgnoreCase(type)) {
            return VoteThreshold.count(section.getInt("count", 1));
        }
        if ("ALL".equalsIgnoreCase(type)) {
            return VoteThreshold.all();
        }
        return VoteThreshold.fraction(section.getInt("numerator", 1), section.getInt("denominator", 2));
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
