package com.leonardobishop.quests.module.hook;

import com.leonardobishop.quests.Quests;

public interface PluginHook {

    String require();

    void load(Quests plugin);

    default void unload() { }

}
