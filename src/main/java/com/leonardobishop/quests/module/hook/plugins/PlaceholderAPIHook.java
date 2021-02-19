package com.leonardobishop.quests.module.hook.plugins;

import com.leonardobishop.quests.Quests;
import com.leonardobishop.quests.api.QuestsPlaceholders;
import com.leonardobishop.quests.module.QLocale;
import com.leonardobishop.quests.module.hook.PluginHook;

public class PlaceholderAPIHook implements PluginHook {

    private QuestsPlaceholders placeholder;

    @Override
    public String require() {
        return "PlaceholderAPI";
    }

    @Override
    public void load(Quests plugin) {
        placeholder = new QuestsPlaceholders(plugin);
        placeholder.register();
        QLocale.papi = true;
    }

    @Override
    public void unload() {
        placeholder.unregister();
    }
}
