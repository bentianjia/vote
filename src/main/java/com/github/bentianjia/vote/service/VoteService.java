package com.github.bentianjia.vote.service;

import com.github.bentianjia.vote.model.VoteChoice;
import com.github.bentianjia.vote.model.VoteRecord;
import com.github.bentianjia.vote.model.VoteStatus;
import com.github.bentianjia.vote.storage.VoteStore;
import com.github.bentianjia.vote.util.CommandParsers;
import com.github.bentianjia.vote.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

public final class VoteService {
    private final JavaPlugin plugin;
    private final VoteStore store;
    private final FakePlayerDetector fakePlayerDetector;

    public VoteService(JavaPlugin plugin, VoteStore store, FakePlayerDetector fakePlayerDetector) {
        this.plugin = plugin;
        this.store = store;
        this.fakePlayerDetector = fakePlayerDetector;
    }

    public VoteRecord createVote(CommandSender sender, CommandParsers.CreateVoteInput input) {
        if (store.activeTitleExists(input.summary())) {
            throw new IllegalArgumentException("已经存在同名投票：" + input.summary());
        }
        UUID creatorUuid = sender instanceof Player player ? player.getUniqueId() : null;
        String creatorName = sender.getName();
        long now = System.currentTimeMillis();
        Long expiresAt = input.timeout() == null ? null : now + input.timeout().toMillis();
        VoteRecord record = new VoteRecord(UUID.randomUUID().toString(), input.summary(), input.details(),
                input.commands(), creatorUuid, creatorName, now, expiresAt, input.threshold(),
                VoteStatus.ACTIVE, false, null);
        store.put(record);
        store.save();
        Bukkit.broadcastMessage(Text.prefix() + Text.color("&e操作员 &f" + creatorName
                + " &e发布了投票：&b" + record.summary() + "&e，请打开 /vote 菜单查看。"));
        return record;
    }

    public void editVote(VoteRecord record, CommandParsers.EditVoteInput input) {
        if (input.summary() != null && !record.summary().equalsIgnoreCase(input.summary())
                && record.isActive()
                && store.activeTitleExists(input.summary(), record.id())) {
            throw new IllegalArgumentException("已经存在同名投票：" + input.summary());
        }
        if (input.summary() != null) {
            record.summary(input.summary());
        }
        if (input.details() != null) {
            record.details(input.details());
        }
        if (input.commands() != null) {
            record.commands(input.commands());
        }
        if (input.timeoutProvided()) {
            record.expiresAt(input.timeout() == null ? null : System.currentTimeMillis() + input.timeout().toMillis());
        }
        if (input.threshold() != null) {
            record.threshold(input.threshold());
        }
        store.save();
    }

    public VoteChoice castVote(Player player, VoteRecord record, VoteChoice choice) {
        if (!record.isActive()) {
            throw new IllegalArgumentException("这个投票已经结束，不能继续投票。");
        }
        if (record.isExpired(System.currentTimeMillis())) {
            expire(record);
            store.save();
            throw new IllegalArgumentException("这个投票已经超时截止。");
        }
        record.setVote(player.getUniqueId(), player.getName(), choice);
        checkPass(record);
        store.save();
        return choice;
    }

    public void tickVotes() {
        boolean changed = false;
        for (VoteRecord record : store.allVotes()) {
            if (!record.isActive()) {
                continue;
            }
            if (checkPass(record)) {
                changed = true;
                continue;
            }
            if (record.isExpired(System.currentTimeMillis())) {
                expire(record);
                changed = true;
            }
        }
        if (changed) {
            store.save();
        }
    }

    public boolean checkPass(VoteRecord record) {
        if (!record.isActive()) {
            return false;
        }
        int online = realOnlinePlayers();
        int required = record.threshold().requiredVotes(online);
        if (record.supportCount() < required) {
            return false;
        }
        record.status(VoteStatus.PASSED);
        record.closedAt(System.currentTimeMillis());
        Bukkit.broadcastMessage(Text.prefix() + Text.color("&a投票已通过：&f" + record.summary()
                + " &7(" + record.supportCount() + "/" + required + ")"));
        executeCommands(record);
        return true;
    }

    public void expire(VoteRecord record) {
        if (!record.isActive()) {
            return;
        }
        record.status(VoteStatus.EXPIRED);
        record.closedAt(System.currentTimeMillis());
        Bukkit.broadcastMessage(Text.prefix() + Text.color("&7投票已截止：&f" + record.summary()));
    }

    public int realOnlinePlayers() {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!fakePlayerDetector.isFake(player)) {
                count++;
            }
        }
        return count;
    }

    public int requiredVotes(VoteRecord record) {
        return record.threshold().requiredVotes(realOnlinePlayers());
    }

    public String statusText(VoteRecord record) {
        return switch (record.status()) {
            case ACTIVE -> "进行中";
            case PASSED -> "已通过";
            case EXPIRED -> "已截止";
        };
    }

    public List<String> commandPreview(VoteRecord record) {
        return record.commands();
    }

    private void executeCommands(VoteRecord record) {
        if (record.commandsExecuted()) {
            return;
        }
        for (String command : record.commands()) {
            String prepared = command
                    .replace("{id}", record.id())
                    .replace("{summary}", record.summary())
                    .replace("{creator}", record.creatorName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), prepared);
        }
        record.commandsExecuted(true);
    }
}
