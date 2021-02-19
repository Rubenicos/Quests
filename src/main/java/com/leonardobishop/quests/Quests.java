package com.leonardobishop.quests;

import com.leonardobishop.quests.bstats.Metrics;
import com.leonardobishop.quests.commands.CommandQuests;
import com.leonardobishop.quests.events.EventInventory;
import com.leonardobishop.quests.events.EventPlayerJoin;
import com.leonardobishop.quests.events.EventPlayerLeave;
import com.leonardobishop.quests.hooks.itemgetter.ItemGetter;
import com.leonardobishop.quests.hooks.itemgetter.ItemGetterLatest;
import com.leonardobishop.quests.hooks.itemgetter.ItemGetter_1_13;
import com.leonardobishop.quests.hooks.itemgetter.ItemGetter_Late_1_8;
import com.leonardobishop.quests.module.Settings;
import com.leonardobishop.quests.module.QFiles;
import com.leonardobishop.quests.module.QLocale;
import com.leonardobishop.quests.module.hook.HookLoader;
import com.leonardobishop.quests.player.QPlayer;
import com.leonardobishop.quests.player.QPlayerManager;
import com.leonardobishop.quests.quests.QuestManager;
import com.leonardobishop.quests.quests.tasktypes.TaskType;
import com.leonardobishop.quests.quests.tasktypes.TaskTypeManager;
import com.leonardobishop.quests.quests.tasktypes.types.*;
import com.leonardobishop.quests.updater.Updater;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;

public class Quests extends JavaPlugin {

    private static Quests quests;

    private static QuestManager questManager;
    private static QPlayerManager qPlayerManager;
    private static TaskTypeManager taskTypeManager;

    private static Updater updater;
    private ItemGetter itemGetter;
    private QuestCompleter questCompleter;
    private Settings settings;
    private QuestsLogger questsLogger;

    private boolean brokenConfig = false;
    private BukkitTask questAutosaveTask;
    private BukkitTask questQueuePollTask;

    public static Quests get() {
        return quests;
    }

    public QuestManager getQuestManager() {
        return questManager;
    }

    public QPlayerManager getPlayerManager() {
        return qPlayerManager;
    }

    public TaskTypeManager getTaskTypeManager() {
        return taskTypeManager;
    }

    public boolean isBrokenConfig() {
        return brokenConfig;
    }

    public void setBrokenConfig(boolean brokenConfig) {
        this.brokenConfig = brokenConfig;
    }

    public Updater getUpdater() {
        return updater;
    }

    public Settings getSettings() {
        return settings;
    }

    public String convertToFormat(long m) { //seconds please
        long hours = m / 3600;
        long minutes = (m % 3600) / 60;
        long seconds = ((m % 3600) % 60) % 60;

        return QLocale.getString("Message.Time-Format")
                .replace("{hours}", String.format("%02d", hours))
                .replace("{minutes}", String.format("%02d", minutes))
                .replace("{seconds}", String.format("%02d", seconds));
    }

    @Override
    public void onEnable() {
        quests = this;

        questsLogger = new QuestsLogger(this, QuestsLogger.LoggingLevel.INFO);
        questCompleter = new QuestCompleter(this);

        taskTypeManager = new TaskTypeManager(this);
        questManager = new QuestManager(this);
        qPlayerManager = new QPlayerManager(this);

        settings = new Settings();

        setupVersionSpecific();

        Bukkit.getPluginCommand("quests").setExecutor(new CommandQuests(this));
        Bukkit.getPluginManager().registerEvents(new EventPlayerJoin(this), this);
        Bukkit.getPluginManager().registerEvents(new EventInventory(this), this);
        Bukkit.getPluginManager().registerEvents(new EventPlayerLeave(this), this);

        Metrics metrics = new Metrics(this);
        if (metrics.isEnabled()) {
            this.getQuestsLogger().info("Metrics started. This can be disabled at /plugins/bStats/config.yml.");
        }

        taskTypeManager.registerTaskType(new MiningTaskType());
        taskTypeManager.registerTaskType(new MiningCertainTaskType());
        taskTypeManager.registerTaskType(new BuildingTaskType());
        taskTypeManager.registerTaskType(new BuildingCertainTaskType());
        taskTypeManager.registerTaskType(new MobkillingTaskType());
        taskTypeManager.registerTaskType(new MobkillingCertainTaskType());
        taskTypeManager.registerTaskType(new PlayerkillingTaskType());
        taskTypeManager.registerTaskType(new FishingTaskType());
        taskTypeManager.registerTaskType(new InventoryTaskType());
        taskTypeManager.registerTaskType(new WalkingTaskType());
        taskTypeManager.registerTaskType(new TamingTaskType());
        taskTypeManager.registerTaskType(new MilkingTaskType());
        taskTypeManager.registerTaskType(new ShearingTaskType());
        taskTypeManager.registerTaskType(new PositionTaskType());
        taskTypeManager.registerTaskType(new PlaytimeTaskType());
        taskTypeManager.registerTaskType(new BrewingTaskType());
        taskTypeManager.registerTaskType(new ExpEarnTaskType());
        taskTypeManager.registerTaskType(new BreedingTaskType());
        taskTypeManager.registerTaskType(new EnchantingTaskType());
        taskTypeManager.registerTaskType(new DealDamageTaskType());
        taskTypeManager.registerTaskType(new PermissionTaskType());
        taskTypeManager.registerTaskType(new DistancefromTaskType());
        taskTypeManager.registerTaskType(new CommandTaskType());
        // TODO: FIX
        // taskTypeManager.registerTaskType(new BrewingCertainTaskType());

        HookLoader.load();
        taskTypeManager.closeRegistrations();

//      if (!questsConfigLoader.getBrokenFiles().isEmpty()) {
//          this.getQuestsLogger().severe("Quests has failed to load the following files:");
//          for (Map.Entry<String, QuestsConfigLoader.ConfigLoadError> entry : questsConfigLoader.getBrokenFiles().entrySet()) {
//              this.getQuestsLogger().severe(" - " + entry.getKey() + ": " + entry.getValue().getMessage());
//          }
//      }

        QLocale.load(settings.getString("Locale.Language"));
        QFiles.reloadQuests();

        for (Player player : Bukkit.getOnlinePlayers()) {
            qPlayerManager.loadPlayer(player.getUniqueId());
        }

        // this intentionally should not be documented
        boolean ignoreUpdates = false;
        try {
            ignoreUpdates = new File(this.getDataFolder() + File.separator + "stfuQuestsUpdate").exists();
        } catch (Throwable ignored) { }


        updater = new Updater(this);
        if (!ignoreUpdates) {
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> updater.check());
        }
    }

    @Override
    public void onDisable() {
        for (TaskType taskType : getTaskTypeManager().getTaskTypes()) {
            try {
                taskType.onDisable();
            } catch (Exception ignored) { }
        }
        for (QPlayer qPlayer : qPlayerManager.getQPlayers()) {
            qPlayer.getQuestProgressFile().saveToDisk(false);
        }
        HookLoader.unload();
    }

    public void reloadQuests() {
        settings.reload();

        questManager.getQuests().clear();
        questManager.getCategories().clear();
        taskTypeManager.resetTaskTypes();

        QLocale.reload(settings.getString("Locale.Language"), false);
        QFiles.reloadQuests();

        long autosaveInterval  = 12000;
        if (!isBrokenConfig()) {
            autosaveInterval  = settings.getLong("options.performance-tweaking.quest-autosave-interval");
        }
        boolean autosaveTaskCancelled = true;
        if (questAutosaveTask != null) {
            try {
                questAutosaveTask.cancel();
            } catch (Exception ex) {
                questsLogger.debug("Cannot cancel and restart quest autosave task");
                autosaveTaskCancelled = false;
            }
        }
        if (autosaveTaskCancelled) {
            questAutosaveTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
                new QuestsAutosaveRunnable(this);
            }, autosaveInterval, autosaveInterval);
        }

        boolean queuePollTaskCancelled = true;
        long queueExecuteInterval = 1;
        if (!isBrokenConfig()) {
            queueExecuteInterval = settings.getLong("options.performance-tweaking.quest-queue-executor-interval");
        }
        if (questQueuePollTask != null) {
            try {
                questQueuePollTask.cancel();
            } catch (Exception ex) {
                questsLogger.debug("Cannot cancel and restart quest autosave task");
                queuePollTaskCancelled = false;
            }
        }
        if (queuePollTaskCancelled) {
            questQueuePollTask = Bukkit.getScheduler().runTaskTimer(this, questCompleter, queueExecuteInterval, queueExecuteInterval);
        }
    }

    public ItemStack getItemStack(String path, ConfigurationSection config, ItemGetter.Filter... excludes) {
        return itemGetter.getItem(path, config, this, excludes);
    }

    public ItemGetter getItemGetter() {
        return itemGetter;
    }

    private void setupVersionSpecific() {
        String version;
        try {
            version = Bukkit.getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3];
        } catch (ArrayIndexOutOfBoundsException e) {
            getQuestsLogger().warning("Failed to resolve server version - some features will not work!");
            itemGetter = new ItemGetter_Late_1_8();
            return;
        }

        getQuestsLogger().info("Your server is running version " + version + ".");

        if (version.startsWith("v1_7") || version.startsWith("v1_8") || version.startsWith("v1_9")
                || version.startsWith("v1_10") || version.startsWith("v1_11") || version.startsWith("v1_12")) {
            itemGetter = new ItemGetter_Late_1_8();
        } else if (version.startsWith("v1_13")) {
            itemGetter = new ItemGetter_1_13();
        } else {
            itemGetter = new ItemGetterLatest();
        }
    }

    public QuestCompleter getQuestCompleter() {
        return questCompleter;
    }

    public QuestsLogger getQuestsLogger() {
        return questsLogger;
    }
}
