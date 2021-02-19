package com.leonardobishop.quests.module.hook;

import com.leonardobishop.quests.Quests;
import com.leonardobishop.quests.module.hook.plugins.PlaceholderAPIHook;
import com.leonardobishop.quests.quests.tasktypes.TaskType;
import com.leonardobishop.quests.quests.tasktypes.types.dependent.*;
import org.bukkit.Bukkit;

import java.util.Arrays;
import java.util.List;

public class HookLoader {

    private static final Quests pl = Quests.get();

    private static final List<PluginHook> hooks = Arrays.asList(
            new PlaceholderAPIHook()
    );

    private static final List<TaskType> tasks = Arrays.asList(
            new ASkyBlockLevelType(),
            new BentoBoxLevelTaskType(),
            new CitizensDeliverTaskType(),
            new CitizensInteractTaskType(),
            new EssentialsBalanceTaskType(),
            new EssentialsMoneyEarnTaskType(),
            new IridiumSkyblockValueType(),
            new MythicMobsKillingType(),
            new PlaceholderAPIEvaluateTaskType(),
            new uSkyBlockLevelType()
    );

    public static void load() {
        hooks.forEach(hook -> {
            if (Bukkit.getPluginManager().isPluginEnabled(hook.require())) {
                hook.load(pl);
            }
        });

        tasks.forEach(task -> {
            if (task.canRegister()) {
                pl.getTaskTypeManager().registerTaskType(task);
            }
        });
    }

    public static void unload() {
        hooks.forEach(PluginHook::unload);
    }

}
