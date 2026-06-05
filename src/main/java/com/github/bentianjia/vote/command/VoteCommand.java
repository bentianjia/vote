package com.github.bentianjia.vote.command;

import com.github.bentianjia.vote.gui.VoteMenu;
import com.github.bentianjia.vote.model.VoteChoice;
import com.github.bentianjia.vote.model.VoteRecord;
import com.github.bentianjia.vote.service.PermissionService;
import com.github.bentianjia.vote.service.RemovalService;
import com.github.bentianjia.vote.service.VoteService;
import com.github.bentianjia.vote.storage.VoteStore;
import com.github.bentianjia.vote.util.CommandParsers;
import com.github.bentianjia.vote.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class VoteCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final VoteStore store;
    private final VoteService voteService;
    private final VoteMenu voteMenu;
    private final PermissionService permissionService;
    private final RemovalService removalService;

    public VoteCommand(JavaPlugin plugin, VoteStore store, VoteService voteService, VoteMenu voteMenu,
                       PermissionService permissionService, RemovalService removalService) {
        this.plugin = plugin;
        this.store = store;
        this.voteService = voteService;
        this.voteMenu = voteMenu;
        this.permissionService = permissionService;
        this.removalService = removalService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Text.prefix() + "控制台请使用 /vote list。");
                return true;
            }
            if (!permissionService.canVote(player)) {
                player.sendMessage(Text.prefix() + Text.color("&c你没有投票权限。"));
                return true;
            }
            voteMenu.openList(player, 0);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "create" -> create(sender, args);
            case "edit" -> edit(sender, args);
            case "remove" -> remove(sender, args);
            case "confirm" -> confirm(sender);
            case "list" -> list(sender);
            case "accept", "yes", "agree", "support", "approve", "赞成", "同意", "接受" ->
                    castByCommand(sender, args, VoteChoice.SUPPORT);
            case "reject", "no", "deny", "oppose", "refuse", "反对", "拒绝", "否决" ->
                    castByCommand(sender, args, VoteChoice.OPPOSE);
            default -> {
                sender.sendMessage(Text.prefix() + Text.color("&c未知子命令。输入 &f/vote &c打开菜单，或使用 &f/vote accept/reject <标题或ID>&c。"));
                yield true;
            }
        };
    }

    private boolean castByCommand(CommandSender sender, String[] args, VoteChoice choice) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.prefix() + Text.color("&c控制台不能参与投票。"));
            return true;
        }
        boolean allowed = choice == VoteChoice.SUPPORT
                ? permissionService.canAccept(player)
                : permissionService.canReject(player);
        if (!allowed) {
            String permission = choice == VoteChoice.SUPPORT ? "vote.accept" : "vote.reject";
            player.sendMessage(Text.prefix() + Text.color("&c你没有这个投票操作权限：&f" + permission));
            return true;
        }
        try {
            VoteRecord record = resolveVoteTarget(args, 1);
            voteService.castVote(player, record, choice);
            player.sendMessage(Text.prefix() + Text.color((choice == VoteChoice.SUPPORT ? "&a已投赞成：" : "&c已投反对：")
                    + "&f" + record.summary()));
        } catch (IllegalArgumentException ex) {
            player.sendMessage(Text.prefix() + Text.color("&c" + ex.getMessage()));
        }
        return true;
    }

    private boolean create(CommandSender sender, String[] args) {
        if (!ensureAdmin(sender)) {
            return true;
        }
        try {
            CommandParsers.CreateVoteInput input = CommandParsers.parseCreate(args);
            VoteRecord record = voteService.createVote(sender, input);
            sender.sendMessage(Text.prefix() + Text.color("&a已创建投票：&f" + record.summary()
                    + " &7ID: " + record.shortId()));
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(Text.prefix() + Text.color("&c" + ex.getMessage()));
        }
        return true;
    }

    private boolean edit(CommandSender sender, String[] args) {
        if (!ensureAdmin(sender)) {
            return true;
        }
        try {
            CommandParsers.EditVoteInput input = CommandParsers.parseEdit(args);
            if (input.isEmpty()) {
                throw new IllegalArgumentException("请至少修改 summary、details、command、timeout 或 amount 一项。");
            }
            VoteRecord record = findVote(input.target());
            voteService.editVote(record, input);
            sender.sendMessage(Text.prefix() + Text.color("&a已编辑投票：&f" + record.summary()));
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(Text.prefix() + Text.color("&c" + ex.getMessage()));
        }
        return true;
    }

    private boolean remove(CommandSender sender, String[] args) {
        if (!ensureAdmin(sender)) {
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Text.prefix() + Text.color("&c用法：/vote remove <标题或ID>"));
            return true;
        }
        String target = Text.join(args, 1, args.length);
        try {
            removalService.request(sender, findVote(target));
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(Text.prefix() + Text.color("&c" + ex.getMessage()));
        }
        return true;
    }

    private boolean confirm(CommandSender sender) {
        if (!ensureAdmin(sender)) {
            return true;
        }
        try {
            VoteRecord removed = removalService.confirm(sender);
            sender.sendMessage(Text.prefix() + Text.color("&a已删除投票：&f" + removed.summary()));
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(Text.prefix() + Text.color("&c" + ex.getMessage()));
        }
        return true;
    }

    private boolean list(CommandSender sender) {
        if (!ensureAdmin(sender)) {
            return true;
        }
        List<VoteRecord> votes = store.allVotes();
        sender.sendMessage(Text.prefix() + Text.color("&e投票列表，共 &f" + votes.size() + " &e项："));
        for (VoteRecord record : votes) {
            int required = voteService.requiredVotes(record);
            sender.sendMessage(Text.color("&7- &b" + record.shortId() + " &f" + record.summary()
                    + " &8[" + voteService.statusText(record) + "] &a赞成 "
                    + record.supportCount() + "/" + required + " &c反对 " + record.opposeCount()
                    + " &7条件 " + record.threshold().display()));
            if (!record.commands().isEmpty()) {
                sender.sendMessage(Text.color("  &8命令: &7" + String.join(" ; ", record.commands())));
            }
        }
        return true;
    }

    private VoteRecord findVote(String target) {
        Optional<VoteRecord> record = store.find(target);
        return record.orElseThrow(() -> new IllegalArgumentException("找不到唯一投票：" + target + "。请使用 /vote list 查看 ID。"));
    }

    private VoteRecord resolveVoteTarget(String[] args, int start) {
        if (args.length > start) {
            return findVote(Text.join(args, start, args.length));
        }
        List<VoteRecord> activeVotes = new ArrayList<>();
        for (VoteRecord record : store.allVotes()) {
            if (record.isActive()) {
                activeVotes.add(record);
            }
        }
        if (activeVotes.isEmpty()) {
            throw new IllegalArgumentException("当前没有进行中的投票。");
        }
        if (activeVotes.size() > 1) {
            throw new IllegalArgumentException("当前有多个投票，请使用 /vote accept <标题或ID> 或 /vote reject <标题或ID>。");
        }
        return activeVotes.get(0);
    }

    private boolean ensureAdmin(CommandSender sender) {
        if (permissionService.canAdmin(sender)) {
            return true;
        }
        sender.sendMessage(Text.prefix() + Text.color("&c这个操作需要 OP 等级 >= 2 或 vote.admin 权限。"));
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            if (permissionService.canAccept(sender)) {
                addMatching(suggestions, args[0], "accept", "yes", "support");
            }
            if (permissionService.canReject(sender)) {
                addMatching(suggestions, args[0], "reject", "no", "oppose");
            }
            if (permissionService.canAdmin(sender)) {
                addMatching(suggestions, args[0], "create", "edit", "remove", "confirm", "list");
            }
            return suggestions;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (isAcceptSubCommand(sub) || isRejectSubCommand(sub)) {
            if ((isAcceptSubCommand(sub) && !permissionService.canAccept(sender))
                    || (isRejectSubCommand(sub) && !permissionService.canReject(sender))) {
                return suggestions;
            }
            for (VoteRecord record : store.allVotes()) {
                if (record.isActive()) {
                    addMatching(suggestions, args[args.length - 1], record.summary(), record.shortId());
                }
            }
            return suggestions;
        }
        if (!permissionService.canAdmin(sender)) {
            return suggestions;
        }
        if ("create".equals(sub)) {
            addMatching(suggestions, args[args.length - 1], "summary:", "details:", "command:", "timeout:", "amount:", "all", "1/2", "2/3", "10p");
        } else if ("edit".equals(sub)) {
            if (args.length == 2) {
                for (VoteRecord record : store.allVotes()) {
                    addMatching(suggestions, args[1], record.summary(), record.shortId());
                }
            } else {
                addMatching(suggestions, args[args.length - 1], "summary:", "details:", "command:", "timeout:", "amount:", "none");
            }
        } else if ("remove".equals(sub) && args.length == 2) {
            for (VoteRecord record : store.allVotes()) {
                addMatching(suggestions, args[1], record.summary(), record.shortId());
            }
        }
        return suggestions;
    }

    private boolean isAcceptSubCommand(String sub) {
        return switch (sub) {
            case "accept", "yes", "agree", "support", "approve", "赞成", "同意", "接受" -> true;
            default -> false;
        };
    }

    private boolean isRejectSubCommand(String sub) {
        return switch (sub) {
            case "reject", "no", "deny", "oppose", "refuse", "反对", "拒绝", "否决" -> true;
            default -> false;
        };
    }

    private void addMatching(List<String> target, String prefix, String... values) {
        String normalized = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                target.add(value);
            }
        }
    }
}
