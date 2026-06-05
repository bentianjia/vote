package com.github.bentianjia.vote.gui;

import com.github.bentianjia.vote.model.VoteChoice;
import com.github.bentianjia.vote.model.VoteRecord;
import com.github.bentianjia.vote.service.ChatWizard;
import com.github.bentianjia.vote.service.PermissionService;
import com.github.bentianjia.vote.service.RemovalService;
import com.github.bentianjia.vote.service.VoteService;
import com.github.bentianjia.vote.storage.VoteStore;
import com.github.bentianjia.vote.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VoteMenu implements Listener {
    private static final int[] LIST_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final JavaPlugin plugin;
    private final VoteStore store;
    private final VoteService voteService;
    private final PermissionService permissionService;
    private final ChatWizard chatWizard;
    private final RemovalService removalService;

    public VoteMenu(JavaPlugin plugin, VoteStore store, VoteService voteService, PermissionService permissionService,
                    ChatWizard chatWizard, RemovalService removalService) {
        this.plugin = plugin;
        this.store = store;
        this.voteService = voteService;
        this.permissionService = permissionService;
        this.chatWizard = chatWizard;
        this.removalService = removalService;
    }

    public void openList(Player player, int page) {
        if (!permissionService.canVote(player)) {
            player.sendMessage(Text.prefix() + Text.color("&c你没有投票权限。"));
            return;
        }
        boolean admin = permissionService.canAdmin(player);
        List<VoteRecord> votes = store.allVotes();
        int maxPage = Math.max(0, (votes.size() - 1) / LIST_SLOTS.length);
        int safePage = Math.max(0, Math.min(page, maxPage));
        ListHolder holder = new ListHolder(safePage);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Text.color(plugin.getConfig().getString("menu.title-prefix", "&8投票") + " &7列表"));
        holder.inventory = inventory;

        fillFrame(inventory);
        int start = safePage * LIST_SLOTS.length;
        for (int i = 0; i < LIST_SLOTS.length; i++) {
            int voteIndex = start + i;
            if (voteIndex >= votes.size()) {
                break;
            }
            VoteRecord record = votes.get(voteIndex);
            holder.voteSlots.put(LIST_SLOTS[i], record.id());
            inventory.setItem(LIST_SLOTS[i], voteItem(record, admin));
        }
        if (votes.isEmpty()) {
            inventory.setItem(22, item(Material.PAPER, "&e暂无投票", List.of("&7管理员可点击下方铁砧新建。")));
        }

        if (safePage > 0) {
            inventory.setItem(45, item(Material.ARROW, "&a上一页", List.of("&7第 " + safePage + " 页")));
        }
        if (safePage < maxPage) {
            inventory.setItem(53, item(Material.ARROW, "&a下一页", List.of("&7第 " + (safePage + 2) + " 页")));
        }
        if (admin) {
            inventory.setItem(46, item(Material.ANVIL, "&b新建投票", List.of("&7点击后在聊天中填写内容。")));
            inventory.setItem(47, item(Material.COMMAND_BLOCK, "&e管理员视图", List.of("&7投票详情中会显示通过命令。")));
        }
        inventory.setItem(49, item(Material.BARRIER, "&c关闭", List.of()));
        inventory.setItem(52, item(Material.CLOCK, "&f在线真实玩家", List.of("&7当前计入人数: " + voteService.realOnlinePlayers())));

        player.openInventory(inventory);
    }

    public void openDetail(Player player, VoteRecord record) {
        if (!permissionService.canVote(player)) {
            player.sendMessage(Text.prefix() + Text.color("&c你没有投票权限。"));
            return;
        }
        boolean admin = permissionService.canAdmin(player);
        DetailHolder holder = new DetailHolder(record.id());
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Text.color(plugin.getConfig().getString("menu.title-prefix", "&8投票") + " &7详情"));
        holder.inventory = inventory;
        fillFrame(inventory);

        inventory.setItem(4, statusItem(record));
        inventory.setItem(22, detailItem(record, admin));
        inventory.setItem(39, item(Material.LIME_CONCRETE, "&a赞成", voteButtonLore(player, record, VoteChoice.SUPPORT)));
        inventory.setItem(41, item(Material.RED_CONCRETE, "&c反对", voteButtonLore(player, record, VoteChoice.OPPOSE)));
        inventory.setItem(49, item(Material.ARROW, "&e返回列表", List.of()));

        if (admin) {
            inventory.setItem(29, item(Material.NAME_TAG, "&b编辑标题", List.of("&7点击后在聊天输入新标题。")));
            inventory.setItem(31, item(Material.OAK_SIGN, "&b编辑详情", List.of("&7点击后在聊天输入新内容。")));
            inventory.setItem(33, item(Material.COMMAND_BLOCK, "&b编辑命令", List.of("&7点击后在聊天输入新命令。")));
            inventory.setItem(45, item(Material.BARRIER, "&c删除投票", List.of("&7点击后仍需 /vote confirm 确认。")));
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof ListHolder) && !(holder instanceof DetailHolder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= top.getSize()) {
            return;
        }
        if (holder instanceof ListHolder listHolder) {
            handleListClick(player, listHolder, event.getRawSlot());
        } else if (holder instanceof DetailHolder detailHolder) {
            handleDetailClick(player, detailHolder, event.getRawSlot());
        }
    }

    private void handleListClick(Player player, ListHolder holder, int slot) {
        if (slot == 49) {
            player.closeInventory();
            return;
        }
        if (slot == 45 && holder.page > 0) {
            openList(player, holder.page - 1);
            return;
        }
        if (slot == 53) {
            openList(player, holder.page + 1);
            return;
        }
        if (slot == 46 && permissionService.canAdmin(player)) {
            chatWizard.startCreate(player);
            return;
        }
        String voteId = holder.voteSlots.get(slot);
        if (voteId == null) {
            return;
        }
        store.byId(voteId).ifPresent(record -> openDetail(player, record));
    }

    private void handleDetailClick(Player player, DetailHolder holder, int slot) {
        VoteRecord record = store.byId(holder.voteId).orElse(null);
        if (record == null) {
            player.closeInventory();
            player.sendMessage(Text.prefix() + Text.color("&c这个投票已经不存在。"));
            return;
        }
        if (slot == 49) {
            openList(player, 0);
            return;
        }
        if (slot == 22) {
            sendDetails(player, record);
            return;
        }
        if (slot == 39 || slot == 41) {
            VoteChoice choice = slot == 39 ? VoteChoice.SUPPORT : VoteChoice.OPPOSE;
            boolean allowed = choice == VoteChoice.SUPPORT
                    ? permissionService.canAccept(player)
                    : permissionService.canReject(player);
            if (!allowed) {
                String permission = choice == VoteChoice.SUPPORT ? "vote.accept" : "vote.reject";
                player.sendMessage(Text.prefix() + Text.color("&c你没有这个投票操作权限：&f" + permission));
                return;
            }
            try {
                voteService.castVote(player, record, choice);
                player.sendMessage(Text.prefix() + Text.color((choice == VoteChoice.SUPPORT ? "&a已投赞成：" : "&c已投反对：")
                        + "&f" + record.summary()));
                openDetail(player, record);
            } catch (IllegalArgumentException ex) {
                player.sendMessage(Text.prefix() + Text.color("&c" + ex.getMessage()));
                openDetail(player, record);
            }
            return;
        }
        if (!permissionService.canAdmin(player)) {
            return;
        }
        switch (slot) {
            case 29 -> chatWizard.startEdit(player, record, ChatWizard.EditField.SUMMARY);
            case 31 -> chatWizard.startEdit(player, record, ChatWizard.EditField.DETAILS);
            case 33 -> chatWizard.startEdit(player, record, ChatWizard.EditField.COMMANDS);
            case 45 -> {
                player.closeInventory();
                removalService.request(player, record);
            }
            default -> {
            }
        }
    }

    private ItemStack voteItem(VoteRecord record, boolean admin) {
        Material material = switch (record.status()) {
            case ACTIVE -> Material.WRITABLE_BOOK;
            case PASSED -> Material.EMERALD_BLOCK;
            case EXPIRED -> Material.CLOCK;
        };
        List<String> lore = new ArrayList<>();
        lore.add(Text.color("&7状态: &f" + voteService.statusText(record)));
        lore.add(Text.color("&7ID: &8" + record.shortId()));
        lore.add(Text.color("&a赞成: &f" + record.supportCount() + "&7/&f" + voteService.requiredVotes(record)));
        lore.add(Text.color("&c反对: &f" + record.opposeCount()));
        lore.add(Text.color("&7条件: &f" + record.threshold().display()));
        lore.add(Text.color("&7剩余: &f" + Text.formatRemaining(record.expiresAt())));
        lore.add(Text.color("&8点击查看详情"));
        if (admin && !record.commands().isEmpty()) {
            lore.add(Text.color("&8通过命令: &7" + String.join(" ; ", record.commands())));
        }
        return item(material, "&b" + record.summary(), lore);
    }

    private ItemStack statusItem(VoteRecord record) {
        List<String> lore = new ArrayList<>();
        lore.add(Text.color("&7创建者: &f" + record.creatorName()));
        lore.add(Text.color("&7创建时间: &f" + Text.formatMillis(record.createdAt())));
        lore.add(Text.color("&7截止: &f" + (record.expiresAt() == null ? "无" : Text.formatMillis(record.expiresAt()))));
        lore.add(Text.color("&7条件: &f" + record.threshold().display()));
        lore.add(Text.color("&a赞成: &f" + record.supportCount() + "&7/&f" + voteService.requiredVotes(record)));
        lore.add(Text.color("&c反对: &f" + record.opposeCount()));
        return item(record.isActive() ? Material.ENDER_EYE : Material.BOOK, "&e" + voteService.statusText(record), lore);
    }

    private ItemStack detailItem(VoteRecord record, boolean admin) {
        List<String> lore = new ArrayList<>();
        lore.add(Text.color("&8悬浮查看，点击发送到你的聊天栏。"));
        lore.add("");
        lore.addAll(Text.wrapLore(record.details(), 28, "&f"));
        if (admin) {
            lore.add("");
            lore.add(Text.color("&8通过后执行命令："));
            if (record.commands().isEmpty()) {
                lore.add(Text.color("&7无"));
            } else {
                for (String command : record.commands()) {
                    lore.addAll(Text.wrapLore(command, 28, "&7"));
                }
            }
        }
        return item(Material.BOOK, "&f" + record.summary(), lore);
    }

    private List<String> voteButtonLore(Player player, VoteRecord record, VoteChoice target) {
        List<String> lore = new ArrayList<>();
        if (!record.isActive()) {
            lore.add(Text.color("&7投票已结束。"));
            return lore;
        }
        boolean allowed = target == VoteChoice.SUPPORT
                ? permissionService.canAccept(player)
                : permissionService.canReject(player);
        if (!allowed) {
            lore.add(Text.color("&c你没有这个操作权限。"));
            return lore;
        }
        VoteChoice current = record.voteOf(player.getUniqueId());
        lore.add(Text.color("&7点击投" + (target == VoteChoice.SUPPORT ? "赞成" : "反对") + "票。"));
        if (current == target) {
            lore.add(Text.color("&e你当前选择了这一项。"));
        } else if (current != null) {
            lore.add(Text.color("&7点击后会覆盖你之前的选择。"));
        }
        return lore;
    }

    private void sendDetails(Player player, VoteRecord record) {
        player.sendMessage(Text.prefix() + Text.color("&e投票：&f" + record.summary()));
        player.sendMessage(Text.color("&7状态: &f" + voteService.statusText(record)
                + " &7赞成: &a" + record.supportCount() + "&7/&f" + voteService.requiredVotes(record)
                + " &7反对: &c" + record.opposeCount()));
        for (String line : Text.wrapLore(record.details(), 80, "&f")) {
            player.sendMessage(line);
        }
        if (permissionService.canAdmin(player) && !record.commands().isEmpty()) {
            player.sendMessage(Text.color("&8通过命令: &7" + String.join(" ; ", record.commands())));
        }
    }

    private void fillFrame(Inventory inventory) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (row == 0 || row == 5 || col == 0 || col == 8) {
                inventory.setItem(slot, filler);
            }
        }
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            List<String> coloredLore = new ArrayList<>(lore.size());
            for (String line : lore) {
                coloredLore.add(line == null || line.isEmpty() ? "" : Text.color(line));
            }
            meta.setLore(coloredLore);
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    private static final class ListHolder implements InventoryHolder {
        private final int page;
        private final Map<Integer, String> voteSlots = new HashMap<>();
        private Inventory inventory;

        private ListHolder(int page) {
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class DetailHolder implements InventoryHolder {
        private final String voteId;
        private Inventory inventory;

        private DetailHolder(String voteId) {
            this.voteId = voteId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
