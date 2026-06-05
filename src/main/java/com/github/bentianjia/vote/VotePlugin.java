package com.github.bentianjia.vote;

import com.github.bentianjia.vote.command.VoteCommand;
import com.github.bentianjia.vote.gui.VoteMenu;
import com.github.bentianjia.vote.service.ChatWizard;
import com.github.bentianjia.vote.service.FakePlayerDetector;
import com.github.bentianjia.vote.service.PermissionService;
import com.github.bentianjia.vote.service.RemovalService;
import com.github.bentianjia.vote.service.VoteService;
import com.github.bentianjia.vote.storage.VoteStore;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class VotePlugin extends JavaPlugin {
    private VoteStore voteStore;
    private VoteService voteService;
    private PermissionService permissionService;
    private VoteMenu voteMenu;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        FakePlayerDetector fakePlayerDetector = new FakePlayerDetector(this);
        permissionService = new PermissionService(this);
        voteStore = new VoteStore(this);
        voteStore.load();

        voteService = new VoteService(this, voteStore, fakePlayerDetector);
        RemovalService removalService = new RemovalService(voteStore);
        ChatWizard chatWizard = new ChatWizard(this, voteService, voteStore);
        voteMenu = new VoteMenu(this, voteStore, voteService, permissionService, chatWizard, removalService);

        VoteCommand voteCommand = new VoteCommand(this, voteStore, voteService, voteMenu, permissionService, removalService);
        PluginCommand command = getCommand("vote");
        if (command != null) {
            command.setExecutor(voteCommand);
            command.setTabCompleter(voteCommand);
        }

        getServer().getPluginManager().registerEvents(voteMenu, this);
        getServer().getPluginManager().registerEvents(chatWizard, this);

        long checkTicks = Math.max(1L, getConfig().getLong("vote.check-expired-every-seconds", 30L)) * 20L;
        getServer().getScheduler().runTaskTimer(this, voteService::tickVotes, checkTicks, checkTicks);
    }

    @Override
    public void onDisable() {
        if (voteStore != null) {
            voteStore.save();
        }
    }

    public VoteMenu voteMenu() {
        return voteMenu;
    }
}

