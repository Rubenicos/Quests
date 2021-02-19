package com.leonardobishop.quests.obj.misc;

import com.leonardobishop.quests.Quests;
import com.leonardobishop.quests.hooks.itemgetter.ItemGetter;
import com.leonardobishop.quests.module.QLocale;
import com.leonardobishop.quests.player.questprogressfile.QuestProgress;
import com.leonardobishop.quests.player.questprogressfile.QuestProgressFile;
import com.leonardobishop.quests.quests.Quest;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QItemStack {

    private final Quests pl = Quests.get();

    private String name;
    private List<String> loreNormal;
    private List<String> loreStarted;
    private final List<String> globalLoreAppendNormal;
    private final List<String> globalLoreAppendNotStarted;
    private final List<String> globalLoreAppendStarted;
    private final List<String> globalLoreAppendTracked;
    private ItemStack startingItemStack;

    public QItemStack(String name, List<String> loreNormal, List<String> loreStarted, ItemStack startingItemStack) {
        this.name = name;
        this.loreNormal = loreNormal;
        this.loreStarted = loreStarted;
        this.startingItemStack = startingItemStack;

        this.globalLoreAppendNormal = QLocale.color(pl.getSettings().getStringList("global-quest-display.lore.append-normal"));
        this.globalLoreAppendNotStarted = QLocale.color(pl.getSettings().getStringList("global-quest-display.lore.append-not-started"));
        this.globalLoreAppendStarted = QLocale.color(pl.getSettings().getStringList("global-quest-display.lore.append-started"));
        this.globalLoreAppendTracked = QLocale.color(pl.getSettings().getStringList("global-quest-display.lore.append-tracked"));
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getLoreNormal() {
        return loreNormal;
    }

    public void setLoreNormal(List<String> loreNormal) {
        this.loreNormal = loreNormal;
    }

    public List<String> getLoreStarted() {
        return loreStarted;
    }

    public void setLoreStarted(List<String> loreStarted) {
        this.loreStarted = loreStarted;
    }

    public ItemStack getStartingItemStack() {
        return startingItemStack;
    }

    public void setStartingItemStack(ItemStack startingItemStack) {
        this.startingItemStack = startingItemStack;
    }

    public ItemStack toItemStack(Quest quest, QuestProgressFile questProgressFile, QuestProgress questProgress) {
        ItemStack is = new ItemStack(startingItemStack);
        ItemMeta ism = is.getItemMeta();
        ism.setDisplayName(name);
        List<String> formattedLore = new ArrayList<>();
        List<String> tempLore = new ArrayList<>();

        if (pl.getSettings().getBoolean("options.global-quest-display-configuration-override") && !globalLoreAppendNormal.isEmpty()) {
            tempLore.addAll(globalLoreAppendNormal);
        } else {
            tempLore.addAll(loreNormal);
            tempLore.addAll(globalLoreAppendNormal);
        }

        Player player = Bukkit.getPlayer(questProgressFile.getPlayerUUID());
        if (questProgressFile.hasStartedQuest(quest)) {
            boolean tracked = quest.getId().equals(questProgressFile.getPlayerPreferences().getTrackedQuestId());
            if (pl.getSettings().getBoolean("options.global-quest-display-configuration-override") && !globalLoreAppendStarted.isEmpty()) {
                if (tracked) {
                    tempLore.addAll(globalLoreAppendTracked);
                } else {
                    tempLore.addAll(globalLoreAppendStarted);
                }
            } else {
                tempLore.addAll(loreStarted);
                if (tracked) {
                    tempLore.addAll(globalLoreAppendTracked);
                } else {
                    tempLore.addAll(globalLoreAppendStarted);
                }
            }
            ism.addEnchant(Enchantment.ARROW_INFINITE, 1, true);
            try {
                ism.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                ism.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            } catch (Exception ignored) {

            }
        } else {
            tempLore.addAll(globalLoreAppendNotStarted);
        }
        if (QLocale.papi && pl.getSettings().getBoolean("options.gui-use-placeholderapi")) {
            ism.setDisplayName(QLocale.setPlaceholders(player, ism.getDisplayName(), false));
        }
        if (questProgress != null) {
            for (String s : tempLore) {
                s = processPlaceholders(s, questProgress);
                if (QLocale.papi && pl.getSettings().getBoolean("options.gui-use-placeholderapi")) {
                    s = QLocale.setPlaceholders(player, s, false);
                }
                formattedLore.add(s);
            }
        }
        ism.setLore(formattedLore);
        is.setItemMeta(ism);
        return is;
    }

    public static String processPlaceholders(String s, QuestProgress questProgress) {
        Matcher m = Pattern.compile("\\{([^}]+)}").matcher(s);
        while (m.find()) {
            String[] parts = m.group(1).split(":");
            if (parts.length > 1) {
                if (questProgress.getTaskProgress(parts[0]) == null) {
                    continue;
                }
                if (parts[1].equals("progress")) {
                    String str = String.valueOf(questProgress.getTaskProgress(parts[0]).getProgress());
                    s = s.replace("{" + m.group(1) + "}", (str.equals("null") ? String.valueOf(0) : str));
                }
                if (parts[1].equals("complete")) {
                    String str = String.valueOf(questProgress.getTaskProgress(parts[0]).isCompleted());
                    s = s.replace("{" + m.group(1) + "}", str);
                }
            }
        }
        return s;
    }

    public static QItemStack getQItemStack(String path, FileConfiguration config) {
        String cName = config.getString(path + ".name", path + ".name");
        List<String> cLoreNormal = config.getStringList(path + ".lore-normal");
        List<String> cLoreStarted = config.getStringList(path + ".lore-started");

        String name;
        List<String> loreNormal = new ArrayList<>();
        if (cLoreNormal != null) {
            for (String s : cLoreNormal) {
                loreNormal.add(ChatColor.translateAlternateColorCodes('&', s));
            }
        }
        List<String> loreStarted = new ArrayList<>();
        if (cLoreStarted != null) {
            for (String s : cLoreStarted) {
                loreStarted.add(ChatColor.translateAlternateColorCodes('&', s));
            }
        }
        name = ChatColor.translateAlternateColorCodes('&', cName);

        ItemStack is = Quests.get().getItemStack(path, config,
                ItemGetter.Filter.DISPLAY_NAME, ItemGetter.Filter.LORE, ItemGetter.Filter.ENCHANTMENTS, ItemGetter.Filter.ITEM_FLAGS);

        return new QItemStack(name, loreNormal, loreStarted, is);
    }
}
