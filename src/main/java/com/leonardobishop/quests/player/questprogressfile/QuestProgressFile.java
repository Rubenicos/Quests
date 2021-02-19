package com.leonardobishop.quests.player.questprogressfile;

import com.leonardobishop.quests.Quests;
import com.leonardobishop.quests.api.QuestsAPI;
import com.leonardobishop.quests.api.enums.QuestStartResult;
import com.leonardobishop.quests.api.events.*;
import com.leonardobishop.quests.module.QLocale;
import com.leonardobishop.quests.player.QPlayer;
import com.leonardobishop.quests.quests.Quest;
import com.leonardobishop.quests.quests.Task;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class QuestProgressFile {

    private final Map<String, QuestProgress> questProgress = new HashMap<>();
    private final QPlayerPreferences playerPreferences;
    private final UUID playerUUID;
    private final Quests plugin;

    public QuestProgressFile(UUID playerUUID, QPlayerPreferences playerPreferences, Quests plugin) {
        this.playerUUID = playerUUID;
        this.playerPreferences = playerPreferences;
        this.plugin = plugin;
    }

    public boolean completeQuest(Quest quest) {
        QuestProgress questProgress = getQuestProgress(quest);
        questProgress.setStarted(false);
        for (TaskProgress taskProgress : questProgress.getTaskProgress()) {
            taskProgress.setCompleted(false);
            taskProgress.setProgress(null);
        }
        questProgress.setCompleted(true);
        questProgress.setCompletedBefore(true);
        questProgress.setCompletionDate(System.currentTimeMillis());
        if (plugin.getSettings().getBoolean("options.allow-quest-track") && plugin.getSettings().getBoolean("options.quest-autotrack") && !(quest.isRepeatable() && !quest.isCooldownEnabled())) {
            trackQuest(null);
        }
        Player player = Bukkit.getPlayer(this.playerUUID);
        if (player != null) {
            QPlayer questPlayer = QuestsAPI.getPlayerManager().getPlayer(this.playerUUID);
            String questFinishMessage = QLocale.getString("Quest.Complete.Success", quest.getDisplayNameStripped());
            // PlayerFinishQuestEvent -- start
            PlayerFinishQuestEvent questFinishEvent = new PlayerFinishQuestEvent(player, questPlayer, questProgress, questFinishMessage);
            Bukkit.getPluginManager().callEvent(questFinishEvent);
            // PlayerFinishQuestEvent -- end
            Bukkit.getServer().getScheduler().runTask(plugin, () -> {
                for (String s : quest.getRewards()) {
                    Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), s.replace("{player}", player.getName())); //TODO PlaceholderAPI support
                }
            });
            if (questFinishEvent.getQuestFinishMessage() != null)
                player.sendMessage(questFinishEvent.getQuestFinishMessage());
            if (plugin.getSettings().getBoolean("options.titles-enabled")) {
                QLocale.sendTitleByName(player, "Quest-Complete", quest.getDisplayNameStripped());
            }
            for (String s : quest.getRewardString()) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', s));
            }
        }
        return true;
    }

    public void trackQuest(Quest quest) {
        Player player = Bukkit.getPlayer(playerUUID);
        if (quest == null) {
            playerPreferences.setTrackedQuestId(null);
            if (player != null) {
                Bukkit.getPluginManager().callEvent(new PlayerStopTrackQuestEvent(player, this));
            }
        } else if (hasStartedQuest(quest)) {
            playerPreferences.setTrackedQuestId(quest.getId());
            if (player != null) {
                Bukkit.getPluginManager().callEvent(new PlayerStartTrackQuestEvent(player, this));
            }
        }
    }

    /**
     * Check if the player can start a quest.
     * <p>
     * Warning: will fail if the player is not online.
     *
     * @param quest the quest to check
     * @return the quest start result
     */
    public QuestStartResult canStartQuest(Quest quest) {
        Player p = Bukkit.getPlayer(playerUUID);
        if (getStartedQuests().size() >= plugin.getSettings().getInt("options.quest-started-limit") && !plugin.getSettings().getBoolean("options.quest-autostart")) {
            return QuestStartResult.QUEST_LIMIT_REACHED;
        }
        QuestProgress questProgress = getQuestProgress(quest);
        if (!quest.isRepeatable() && questProgress.isCompletedBefore()) {
            //if (playerUUID != null) {
            // ???
            //}
            return QuestStartResult.QUEST_ALREADY_COMPLETED;
        }
        long cooldown = getCooldownFor(quest);
        if (cooldown > 0) {
            return QuestStartResult.QUEST_COOLDOWN;
        }
        if (!hasMetRequirements(quest)) {
            return QuestStartResult.QUEST_LOCKED;
        }
        if (questProgress.isStarted()) {
            return QuestStartResult.QUEST_ALREADY_STARTED;
        }
        if (quest.isPermissionRequired()) {
            if (playerUUID != null) {
                if (!p.hasPermission("quests.quest." + quest.getId())) {
                    return QuestStartResult.QUEST_NO_PERMISSION;
                }
            } else {
                return QuestStartResult.QUEST_NO_PERMISSION;
            }
        }
        if (quest.getCategoryId() != null && plugin.getQuestManager().getCategoryById(quest.getCategoryId()) != null && plugin.getQuestManager()
                .getCategoryById(quest.getCategoryId()).isPermissionRequired()) {
            if (playerUUID != null) {
                if (!p.hasPermission("quests.category." + quest.getCategoryId())) {
                    return QuestStartResult.NO_PERMISSION_FOR_CATEGORY;
                }
            } else {
                return QuestStartResult.NO_PERMISSION_FOR_CATEGORY;
            }
        }
        return QuestStartResult.QUEST_SUCCESS;
    }

    /**
     * Start a quest for the player.
     * <p>
     * Warning: will fail if the player is not online.
     *
     * @param quest the quest to check
     * @return the quest start result
     */
    // TODO PlaceholderAPI support
    public QuestStartResult startQuest(Quest quest) {
        Player player = Bukkit.getPlayer(playerUUID);
        QuestStartResult code = canStartQuest(quest);
        if (player != null) {
            String questResultMessage = null;
            switch (code) {
                case QUEST_SUCCESS:
                    // This one is hacky
                    break;
                case QUEST_LIMIT_REACHED:
                    questResultMessage = QLocale.getString("Quest.Start.Fail.Limit", String.valueOf(plugin.getSettings().getInt("options.quest-started-limit")));
                    break;
                case QUEST_ALREADY_COMPLETED:
                    questResultMessage = QLocale.getString("Quest.Start.Fail.Disabled");
                    break;
                case QUEST_COOLDOWN:
                    long cooldown = getCooldownFor(quest);
                    questResultMessage = QLocale.getString("Quest.Start.Fail.Cooldown", String.valueOf(plugin.convertToFormat(TimeUnit.SECONDS.convert(cooldown, TimeUnit.MILLISECONDS))));
                    break;
                case QUEST_LOCKED:
                    questResultMessage = QLocale.getString("Quest.Start.Fail.Locked");
                    break;
                case QUEST_ALREADY_STARTED:
                    questResultMessage = QLocale.getString("Quest.Start.Fail.Started");
                    break;
                case QUEST_NO_PERMISSION:
                    questResultMessage = QLocale.getString("Quest.Start.Fail.Permission");
                    break;
                case NO_PERMISSION_FOR_CATEGORY:
                    questResultMessage = QLocale.getString("Quest.Start.Fail.Quest-Permission");
                    break;
            }
            QPlayer questPlayer = QuestsAPI.getPlayerManager().getPlayer(this.playerUUID);
            // PreStartQuestEvent -- start
            PreStartQuestEvent preStartQuestEvent = new PreStartQuestEvent(player, questPlayer, questResultMessage, code);
            Bukkit.getPluginManager().callEvent(preStartQuestEvent);
            // PreStartQuestEvent -- end
            if (preStartQuestEvent.getQuestResultMessage() != null && code != QuestStartResult.QUEST_SUCCESS)
                player.sendMessage(preStartQuestEvent.getQuestResultMessage());
        }
        if (code == QuestStartResult.QUEST_SUCCESS) {
            QuestProgress questProgress = getQuestProgress(quest);
            questProgress.setStarted(true);
            for (TaskProgress taskProgress : questProgress.getTaskProgress()) {
                taskProgress.setCompleted(false);
                taskProgress.setProgress(null);
            }
            if (plugin.getSettings().getBoolean("options.allow-quest-track") && plugin.getSettings().getBoolean("options.quest-autotrack")) {
                trackQuest(quest);
            }
            questProgress.setCompleted(false);
            if (player != null) {
                QPlayer questPlayer = QuestsAPI.getPlayerManager().getPlayer(this.playerUUID);
                String questStartMessage = QLocale.getString("Quest.Start.Success", quest.getDisplayNameStripped());
                // PlayerStartQuestEvent -- start
                PlayerStartQuestEvent questStartEvent = new PlayerStartQuestEvent(player, questPlayer, questProgress, questStartMessage);
                Bukkit.getPluginManager().callEvent(questStartEvent);
                // PlayerStartQuestEvent -- end
                if (questStartEvent.getQuestStartMessage() != null)
                    player.sendMessage(questStartEvent.getQuestStartMessage()); //Don't send a message if the event message is null
                if (plugin.getSettings().getBoolean("options.titles-enabled")) {
                    QLocale.sendTitleByName(player, "Quest-Started", quest.getDisplayNameStripped());
                }
                for (String s : quest.getStartString()) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', s));
                }
            }
            for (Task task : quest.getTasks()) {
                try {
                    plugin.getTaskTypeManager().getTaskType(task.getType()).onStart(quest, task, playerUUID);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return code;
    }

    public boolean cancelQuest(Quest quest) {
        QuestProgress questProgress = getQuestProgress(quest);
        Player player = Bukkit.getPlayer(this.playerUUID);
        if (!questProgress.isStarted()) {
            if (player != null) {
                QLocale.sendTo(player, "Quest.Cancel.Not-Started");
            }
            return false;
        }
        questProgress.setStarted(false);
        for (TaskProgress taskProgress : questProgress.getTaskProgress()) {
            taskProgress.setProgress(null);
        }
        if (player != null) {
            QPlayer questPlayer = QuestsAPI.getPlayerManager().getPlayer(this.playerUUID);
            String questCancelMessage = QLocale.getString("Quest.Cancel.Success", quest.getDisplayNameStripped());
            // PlayerCancelQuestEvent -- start
            PlayerCancelQuestEvent questCancelEvent = new PlayerCancelQuestEvent(player, questPlayer, questProgress, questCancelMessage);
            Bukkit.getPluginManager().callEvent(questCancelEvent);
            // PlayerCancelQuestEvent -- end
            if (questCancelEvent.getQuestCancelMessage() != null)
                player.sendMessage(questCancelEvent.getQuestCancelMessage());
        }
        return true;
    }

    public void addQuestProgress(QuestProgress questProgress) {
        this.questProgress.put(questProgress.getQuestId(), questProgress);
    }

    public List<Quest> getStartedQuests() {
        List<Quest> startedQuests = new ArrayList<>();
        for (QuestProgress questProgress : questProgress.values()) {
            if (questProgress.isStarted()) {
                startedQuests.add(plugin.getQuestManager().getQuestById(questProgress.getQuestId()));
            }
        }
        return startedQuests;
    }

    /**
     * @return {@code List<Quest>} all quest
     * @deprecated use {@code getAllQuestsFromProgress(QuestsProgressFilter)} instead
     * <p>
     * Returns all {@code Quest}s a player has encountered
     * (not to be confused with a collection of quest progress)
     */
    @Deprecated
    public List<Quest> getQuestsProgress(String filter) {
        return getAllQuestsFromProgress(QuestsProgressFilter.fromLegacy(filter));
    }

    /**
     * Returns all {@code Quest}s a player has encountered
     * (not to be confused with a collection of quest progress)
     *
     * @return {@code List<Quest>} all quests
     */
    public List<Quest> getAllQuestsFromProgress(QuestsProgressFilter filter) {
        List<Quest> questsProgress = new ArrayList<>();
        for (QuestProgress qProgress : questProgress.values()) {
            boolean condition = false;
            if (filter == QuestsProgressFilter.STARTED) {
                condition = qProgress.isStarted();
            } else if (filter == QuestsProgressFilter.COMPLETED_BEFORE) {
                condition = qProgress.isCompletedBefore();
            } else if (filter == QuestsProgressFilter.COMPLETED) {
                condition = qProgress.isCompleted();
            } else if (filter == QuestsProgressFilter.ALL) {
                condition = true;
            }
            if (condition) {
                Quest quest = plugin.getQuestManager().getQuestById(qProgress.getQuestId());
                if (quest != null) {
                    questsProgress.add(quest);
                }
            }
        }
        return questsProgress;
    }

    public enum QuestsProgressFilter {
        ALL("all"),
        COMPLETED("completed"),
        COMPLETED_BEFORE("completedBefore"),
        STARTED("started");

        private String legacy;

        QuestsProgressFilter(String legacy) {
            this.legacy = legacy;
        }

        public static QuestsProgressFilter fromLegacy(String filter) {
            for (QuestsProgressFilter filterEnum : QuestsProgressFilter.values()) {
                if (filterEnum.getLegacy().equals(filter)) return filterEnum;
            }
            return QuestsProgressFilter.ALL;
        }

        public String getLegacy() {
            return legacy;
        }
    }

    /**
     * Gets all the quest progress that it has ever encountered.
     *
     * @return {@code Collection<QuestProgress>} all quest progresses
     */
    public Collection<QuestProgress> getAllQuestProgress() {
        return questProgress.values();
    }

    public boolean hasQuestProgress(Quest quest) {
        return questProgress.containsKey(quest.getId());
    }

    public boolean hasStartedQuest(Quest quest) {
        if (plugin.getSettings().getBoolean("options.quest-autostart")) {
            QuestStartResult response = canStartQuest(quest);
            return response == QuestStartResult.QUEST_SUCCESS || response == QuestStartResult.QUEST_ALREADY_STARTED;
        } else {
            return hasQuestProgress(quest) && getQuestProgress(quest).isStarted();
        }
    }

    public long getCooldownFor(Quest quest) {
        QuestProgress questProgress = getQuestProgress(quest);
        if (quest.isCooldownEnabled() && questProgress.isCompleted()) {
            if (questProgress.getCompletionDate() > 0) {
                long date = questProgress.getCompletionDate();
                return (date + TimeUnit.MILLISECONDS.convert(quest.getCooldown(), TimeUnit.MINUTES)) - System.currentTimeMillis();
            }
        }
        return 0;
    }

    public boolean hasMetRequirements(Quest quest) {
        for (String id : quest.getRequirements()) {
            Quest q = plugin.getQuestManager().getQuestById(id);
            if (q == null) {
                continue;
            }
            if (hasQuestProgress(q) && !getQuestProgress(q).isCompletedBefore()) {
                return false;
            } else if (!hasQuestProgress(q)) {
                return false;
            }
        }
        return true;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public QuestProgress getQuestProgress(Quest quest) {
        if (questProgress.containsKey(quest.getId())) {
            return questProgress.get(quest.getId());
        } else if (generateBlankQuestProgress(quest.getId())) {
            return getQuestProgress(quest);
        }
        return null;
    }

    public boolean generateBlankQuestProgress(String questid) {
        if (plugin.getQuestManager().getQuestById(questid) != null) {
            Quest quest = plugin.getQuestManager().getQuestById(questid);
            QuestProgress questProgress = new QuestProgress(plugin, quest.getId(), false, false, 0, playerUUID, false, false);
            for (Task task : quest.getTasks()) {
                TaskProgress taskProgress = new TaskProgress(questProgress, task.getId(), null, playerUUID, false, false);
                questProgress.addTaskProgress(taskProgress);
            }

            addQuestProgress(questProgress);
            return true;
        }
        return false;
    }

    public QPlayerPreferences getPlayerPreferences() {
        return playerPreferences;
    }

    public void saveToDisk(boolean async) {
        plugin.getQuestsLogger().debug("Saving player " + playerUUID + " to disk.");
        File directory = new File(plugin.getDataFolder() + File.separator + "playerdata");
        if (!directory.exists() && !directory.isDirectory()) {
            directory.mkdirs();
        }
        File file = new File(plugin.getDataFolder() + File.separator + "playerdata" + File.separator + playerUUID.toString() + ".yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        data.set("quest-progress", null);
        for (QuestProgress questProgress : questProgress.values()) {
            data.set("quest-progress." + questProgress.getQuestId() + ".started", questProgress.isStarted());
            data.set("quest-progress." + questProgress.getQuestId() + ".completed", questProgress.isCompleted());
            data.set("quest-progress." + questProgress.getQuestId() + ".completed-before", questProgress.isCompletedBefore());
            data.set("quest-progress." + questProgress.getQuestId() + ".completion-date", questProgress.getCompletionDate());
            for (TaskProgress taskProgress : questProgress.getTaskProgress()) {
                data.set("quest-progress." + questProgress.getQuestId() + ".task-progress." + taskProgress.getTaskId() + ".completed", taskProgress
                        .isCompleted());
                data.set("quest-progress." + questProgress.getQuestId() + ".task-progress." + taskProgress.getTaskId() + ".progress", taskProgress
                        .getProgress());
            }
        }

        synchronized (this) {

            // TODO
            if (async && plugin.getSettings().getBoolean("options.performance-tweaking.quests-autosave-async")) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        data.save(file);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            } else {
                try {
                    data.save(file);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void clear() {
        questProgress.clear();
    }

    /**
     * Removes any references to quests or tasks which are no longer defined in the config
     */
    public void clean() {
        plugin.getQuestsLogger().debug("Cleaning file " + playerUUID + ".");
        if (!plugin.getTaskTypeManager().areRegistrationsAccepted()) {
            ArrayList<String> invalidQuests = new ArrayList<>();
            for (String questId : this.questProgress.keySet()) {
                Quest q;
                if ((q = plugin.getQuestManager().getQuestById(questId)) == null) {
                    invalidQuests.add(questId);
                } else {
                    ArrayList<String> invalidTasks = new ArrayList<>();
                    for (String taskId : this.questProgress.get(questId).getTaskProgressMap().keySet()) {
                        if (q.getTaskById(taskId) == null) {
                            invalidTasks.add(taskId);
                        }
                    }
                    for (String taskId : invalidTasks) {
                        this.questProgress.get(questId).getTaskProgressMap().remove(taskId);
                    }
                }
            }
            for (String questId : invalidQuests) {
                this.questProgress.remove(questId);
            }
        }
    }

}
