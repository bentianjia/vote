package com.github.bentianjia.vote.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class VoteRecord {
    private final String id;
    private String summary;
    private String details;
    private List<String> commands;
    private final UUID creatorUuid;
    private final String creatorName;
    private final long createdAt;
    private Long expiresAt;
    private VoteThreshold threshold;
    private VoteStatus status;
    private boolean commandsExecuted;
    private Long closedAt;
    private final Map<UUID, VoteChoice> votes = new LinkedHashMap<>();
    private final Map<UUID, String> voterNames = new LinkedHashMap<>();

    public VoteRecord(String id, String summary, String details, List<String> commands, UUID creatorUuid,
                      String creatorName, long createdAt, Long expiresAt, VoteThreshold threshold,
                      VoteStatus status, boolean commandsExecuted, Long closedAt) {
        this.id = id;
        this.summary = summary;
        this.details = details;
        this.commands = new ArrayList<>(commands);
        this.creatorUuid = creatorUuid;
        this.creatorName = creatorName;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.threshold = threshold;
        this.status = status;
        this.commandsExecuted = commandsExecuted;
        this.closedAt = closedAt;
    }

    public String id() {
        return id;
    }

    public String shortId() {
        return id.length() <= 8 ? id : id.substring(0, 8);
    }

    public String summary() {
        return summary;
    }

    public void summary(String summary) {
        this.summary = summary;
    }

    public String details() {
        return details;
    }

    public void details(String details) {
        this.details = details;
    }

    public List<String> commands() {
        return Collections.unmodifiableList(commands);
    }

    public void commands(List<String> commands) {
        this.commands = new ArrayList<>(commands);
        this.commandsExecuted = false;
    }

    public UUID creatorUuid() {
        return creatorUuid;
    }

    public String creatorName() {
        return creatorName;
    }

    public long createdAt() {
        return createdAt;
    }

    public Long expiresAt() {
        return expiresAt;
    }

    public void expiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
    }

    public VoteThreshold threshold() {
        return threshold;
    }

    public void threshold(VoteThreshold threshold) {
        this.threshold = threshold;
    }

    public VoteStatus status() {
        return status;
    }

    public void status(VoteStatus status) {
        this.status = status;
    }

    public boolean commandsExecuted() {
        return commandsExecuted;
    }

    public void commandsExecuted(boolean commandsExecuted) {
        this.commandsExecuted = commandsExecuted;
    }

    public Long closedAt() {
        return closedAt;
    }

    public void closedAt(Long closedAt) {
        this.closedAt = closedAt;
    }

    public Map<UUID, VoteChoice> votes() {
        return votes;
    }

    public Map<UUID, String> voterNames() {
        return voterNames;
    }

    public void setVote(UUID uuid, String name, VoteChoice choice) {
        votes.put(uuid, choice);
        voterNames.put(uuid, name);
    }

    public VoteChoice voteOf(UUID uuid) {
        return votes.get(uuid);
    }

    public int supportCount() {
        int count = 0;
        for (VoteChoice choice : votes.values()) {
            if (choice == VoteChoice.SUPPORT) {
                count++;
            }
        }
        return count;
    }

    public int opposeCount() {
        int count = 0;
        for (VoteChoice choice : votes.values()) {
            if (choice == VoteChoice.OPPOSE) {
                count++;
            }
        }
        return count;
    }

    public boolean isActive() {
        return status == VoteStatus.ACTIVE;
    }

    public boolean isExpired(long nowMillis) {
        return expiresAt != null && nowMillis >= expiresAt;
    }
}

