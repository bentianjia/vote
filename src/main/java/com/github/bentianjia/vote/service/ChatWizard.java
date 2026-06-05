package com.github.bentianjia.vote.service;

import com.github.bentianjia.vote.model.VoteRecord;
import com.github.bentianjia.vote.model.VoteThreshold;
import com.github.bentianjia.vote.storage.VoteStore;
import com.github.bentianjia.vote.util.CommandParsers;
import com.github.bentianjia.vote.util.DurationParser;
import com.github.bentianjia.vote.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ChatWizard implements Listener {
    private final JavaPlugin plugin;
    private final VoteService voteService;
    private final VoteStore voteStore;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public ChatWizard(JavaPlugin plugin, VoteService voteService, VoteStore voteStore) {
        this.plugin = plugin;
        this.voteService = voteService;
        this.voteStore = voteStore;
    }

    public void startCreate(Player player) {
        player.closeInventory();
        Session session = Session.create();
        sessions.put(player.getUniqueId(), session);
        player.sendMessage(Text.prefix() + Text.color("&e开始创建投票。输入 &ccancel &e或 &c取消 &e可退出。"));
        promptCreate(player, session);
    }

    public void startEdit(Player player, VoteRecord record, EditField field) {
        player.closeInventory();
        sessions.put(player.getUniqueId(), Session.edit(record.id(), field));
        String label = switch (field) {
            case SUMMARY -> "新标题";
            case DETAILS -> "新详细内容";
            case COMMANDS -> "新通过命令，不用加 /，用 ; 分隔，输入 none 清空";
        };
        player.sendMessage(Text.prefix() + Text.color("&e请输入" + label + "。输入 &ccancel &e或 &c取消 &e可退出。"));
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!sessions.containsKey(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage();
        plugin.getServer().getScheduler().runTask(plugin, () -> handle(player, message));
    }

    private void handle(Player player, String message) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        String trimmed = message.trim();
        if (trimmed.equalsIgnoreCase("cancel") || trimmed.equalsIgnoreCase("取消")) {
            sessions.remove(player.getUniqueId());
            player.sendMessage(Text.prefix() + Text.color("&7已取消。"));
            return;
        }
        try {
            if (session.mode == Mode.CREATE) {
                handleCreate(player, session, trimmed);
            } else {
                handleEdit(player, session, trimmed);
            }
        } catch (IllegalArgumentException ex) {
            player.sendMessage(Text.prefix() + Text.color("&c" + ex.getMessage()));
            if (session.mode == Mode.CREATE) {
                promptCreate(player, session);
            }
        }
    }

    private void handleCreate(Player player, Session session, String message) {
        switch (session.createStep) {
            case SUMMARY -> {
                if (message.isBlank()) {
                    throw new IllegalArgumentException("标题不能为空。");
                }
                session.summary = message;
                session.createStep = CreateStep.DETAILS;
                promptCreate(player, session);
            }
            case DETAILS -> {
                if (message.isBlank()) {
                    throw new IllegalArgumentException("详细内容不能为空。");
                }
                session.details = message;
                session.createStep = CreateStep.COMMANDS;
                promptCreate(player, session);
            }
            case COMMANDS -> {
                session.commands = CommandParsers.parseCommandList(message);
                session.createStep = CreateStep.TIMEOUT;
                promptCreate(player, session);
            }
            case TIMEOUT -> {
                if (message.equalsIgnoreCase("none") || message.equalsIgnoreCase("无")) {
                    session.timeout = null;
                } else {
                    session.timeout = DurationParser.parse(message);
                }
                session.createStep = CreateStep.THRESHOLD;
                promptCreate(player, session);
            }
            case THRESHOLD -> {
                VoteThreshold threshold = VoteThreshold.parse(message);
                CommandParsers.CreateVoteInput input = new CommandParsers.CreateVoteInput(
                        session.summary, session.details, session.commands, session.timeout, threshold);
                VoteRecord record = voteService.createVote(player, input);
                sessions.remove(player.getUniqueId());
                player.sendMessage(Text.prefix() + Text.color("&a创建完成：&f" + record.summary()));
            }
        }
    }

    private void handleEdit(Player player, Session session, String message) {
        VoteRecord record = voteStore.byId(session.voteId)
                .orElseThrow(() -> new IllegalArgumentException("这个投票已经不存在。"));
        CommandParsers.EditVoteInput input = switch (session.editField) {
            case SUMMARY -> new CommandParsers.EditVoteInput(record.id(), message, null, null, false, null, null);
            case DETAILS -> new CommandParsers.EditVoteInput(record.id(), null, message, null, false, null, null);
            case COMMANDS -> new CommandParsers.EditVoteInput(record.id(), null, null,
                    CommandParsers.parseCommandList(message), false, null, null);
        };
        voteService.editVote(record, input);
        sessions.remove(player.getUniqueId());
        player.sendMessage(Text.prefix() + Text.color("&a已更新投票：&f" + record.summary()));
    }

    private void promptCreate(Player player, Session session) {
        String message = switch (session.createStep) {
            case SUMMARY -> "&e请输入投票标题/主题：";
            case DETAILS -> "&e请输入详细内容：";
            case COMMANDS -> "&e请输入通过后由控制台执行的命令，不用加 &f/&e，用 &f; &e分隔；输入 &fnone &e跳过：";
            case TIMEOUT -> "&e请输入截止时间，例如 &f30m&e、&f1.5h&e、&f2d&e；输入 &fnone &e表示不自动截止：";
            case THRESHOLD -> "&e请输入 amount 通过条件，例如 &f1/2&e、&f10p&e，或 &fall&e：";
        };
        player.sendMessage(Text.prefix() + Text.color(message));
    }

    public enum EditField {
        SUMMARY,
        DETAILS,
        COMMANDS
    }

    private enum Mode {
        CREATE,
        EDIT
    }

    private enum CreateStep {
        SUMMARY,
        DETAILS,
        COMMANDS,
        TIMEOUT,
        THRESHOLD
    }

    private static final class Session {
        private final Mode mode;
        private CreateStep createStep;
        private String voteId;
        private EditField editField;
        private String summary;
        private String details;
        private List<String> commands = List.of();
        private Duration timeout;

        private Session(Mode mode) {
            this.mode = mode;
        }

        private static Session create() {
            Session session = new Session(Mode.CREATE);
            session.createStep = CreateStep.SUMMARY;
            return session;
        }

        private static Session edit(String voteId, EditField field) {
            Session session = new Session(Mode.EDIT);
            session.voteId = voteId;
            session.editField = field;
            return session;
        }
    }
}
