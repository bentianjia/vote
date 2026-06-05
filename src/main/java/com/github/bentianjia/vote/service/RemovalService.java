package com.github.bentianjia.vote.service;

import com.github.bentianjia.vote.model.VoteRecord;
import com.github.bentianjia.vote.storage.VoteStore;
import com.github.bentianjia.vote.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RemovalService {
    private static final long CONFIRM_WINDOW_MILLIS = 30_000L;

    private final VoteStore store;
    private final Map<String, PendingRemoval> pending = new HashMap<>();

    public RemovalService(VoteStore store) {
        this.store = store;
    }

    public void request(CommandSender sender, VoteRecord record) {
        pending.put(senderKey(sender), new PendingRemoval(record.id(), System.currentTimeMillis() + CONFIRM_WINDOW_MILLIS));
        sender.sendMessage(Text.prefix() + Text.color("&e确认删除投票 &f" + record.summary()
                + "&e？请在 30 秒内输入 &b/vote confirm&e。"));
    }

    public VoteRecord confirm(CommandSender sender) {
        String key = senderKey(sender);
        PendingRemoval removal = pending.remove(key);
        if (removal == null) {
            throw new IllegalArgumentException("没有待确认的删除操作。");
        }
        if (System.currentTimeMillis() > removal.expiresAt()) {
            throw new IllegalArgumentException("删除确认已超时，请重新输入 /vote remove。");
        }
        VoteRecord record = store.byId(removal.voteId())
                .orElseThrow(() -> new IllegalArgumentException("待删除的投票已经不存在。"));
        store.remove(record.id());
        store.save();
        return record;
    }

    private String senderKey(CommandSender sender) {
        if (sender instanceof Player player) {
            UUID uuid = player.getUniqueId();
            return uuid.toString();
        }
        return "sender:" + sender.getName().toLowerCase();
    }

    private record PendingRemoval(String voteId, long expiresAt) {
    }
}

