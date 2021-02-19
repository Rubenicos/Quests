package com.leonardobishop.quests.events;

import com.leonardobishop.quests.Quests;
import com.leonardobishop.quests.module.QLocale;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

public class EventPlayerJoin implements Listener {

    private final Quests plugin;

    public EventPlayerJoin(Quests plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEvent(PlayerJoinEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        plugin.getPlayerManager().loadPlayer(playerUuid);
        if (plugin.getSettings().getBoolean("options.soft-clean-questsprogressfile-on-join")) {
            plugin.getPlayerManager().getPlayer(playerUuid).getQuestProgressFile().clean();
            if (plugin.getSettings().getBoolean("options.tab-completion.push-soft-clean-to-disk")) {
                plugin.getPlayerManager().getPlayer(playerUuid).getQuestProgressFile().saveToDisk(false);
            }
        }
        if (plugin.getDescription().getVersion().contains("beta") && event.getPlayer().hasPermission("quests.admin")) {
            QLocale.sendTo(event.getPlayer(), "Message.Beta-Reminder");
        }
        if (plugin.getUpdater().isUpdateReady() && event.getPlayer().hasPermission("quests.admin")) {
            // delay for a bit so they actually see the message
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> event.getPlayer().sendMessage(plugin.getUpdater().getMessage()), 50L);
        }

        // run a full check to check for any missed quest completions
        plugin.getQuestCompleter().queueFullCheck(plugin.getPlayerManager().getPlayer(playerUuid).getQuestProgressFile());
    }

}
