package com.leonardobishop.quests.module;

import com.leonardobishop.quests.Quests;
import com.leonardobishop.quests.QuestsLogger;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Settings {

    private final Quests pl = Quests.get();

    private final FileConfiguration defaultConfig;
    // TODO: Make a better path cache to move this class to static
    private final Map<String, Object> cache = new HashMap<>();

    // TODO: Make a config updater
    public Settings() {
        defaultConfig = QFiles.getFromPlugin("config.yml");
        reload();
    }

    public void reload() {
        pl.saveDefaultConfig();
        pl.reloadConfig();
        cache.clear();
        QFiles.resetCount();
        if (!QFiles.isSafe("<MAIN CONFIG> ", QFiles.getFromFolder("config.yml"))) pl.setBrokenConfig(true);
        pl.getQuestsLogger().setServerLoggingLevel(QuestsLogger.LoggingLevel.fromNumber(getInt("options.verbose-logging-level")));
    }

    public int getInt(String path) {
        return (int) cache.getOrDefault(path, cache(path, pl.getConfig().getInt(path, defaultConfig.getInt(path))));
    }

    public Long getLong(String path) {
        return (Long) cache.getOrDefault(path, cache(path, pl.getConfig().getLong(path, defaultConfig.getLong(path))));
    }

    public String getString(String path) {
        return (String) cache.getOrDefault(path, cache(path, pl.getConfig().getString(path, defaultConfig.getString(path))));
    }

    public List<String> getStringList(String path) {
        return pl.getConfig().getStringList(path);
    }

    public boolean getBoolean(String path) {
        return (boolean) cache.getOrDefault(path, cache(path, pl.getConfig().getBoolean(path, defaultConfig.getBoolean(path))));
    }

    private Object cache(String path, Object object) {
        cache.put(path, object);
        return object;
    }
}
